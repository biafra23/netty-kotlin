/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.util.internal

import io.netty.util.CharsetUtil
import io.netty.util.internal.logging.InternalLoggerFactory

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.URL
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import java.security.AccessController
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.security.PrivilegedAction
import java.util.Arrays
import java.util.Collections
import java.util.EnumSet
import java.util.concurrent.ThreadLocalRandom

/**
 * Helper class to load JNI resources.
 */
object NativeLibraryLoader {

    private val logger = InternalLoggerFactory.getInstance(NativeLibraryLoader::class.java)

    private const val NATIVE_RESOURCE_HOME = "META-INF/native/"

    @JvmField
    val WORKDIR: File

    private val DELETE_NATIVE_LIB_AFTER_LOADING: Boolean
    private val TRY_TO_PATCH_SHADED_ID: Boolean
    private val DETECT_NATIVE_LIBRARY_DUPLICATES: Boolean

    // Just use a-Z and numbers as valid ID bytes.
    private val UNIQUE_ID_BYTES: ByteArray =
        "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ".toByteArray(CharsetUtil.US_ASCII)

    init {
        val workdir = SystemPropertyUtil.get("io.netty.native.workdir")
        if (workdir != null) {
            var f = File(workdir)
            if (!f.exists() && !f.mkdirs()) {
                throw ExceptionInInitializerError(
                    IOException("Custom native workdir mkdirs failed: $workdir")
                )
            }

            try {
                f = f.absoluteFile
            } catch (ignored: Exception) {
                // Good to have an absolute path, but it's OK.
            }

            WORKDIR = f
            logger.debug("-Dio.netty.native.workdir: $WORKDIR")
        } else {
            WORKDIR = PlatformDependent.tmpdir()
            logger.debug("-Dio.netty.native.workdir: $WORKDIR (io.netty.tmpdir)")
        }

        DELETE_NATIVE_LIB_AFTER_LOADING = SystemPropertyUtil.getBoolean(
            "io.netty.native.deleteLibAfterLoading", true
        )
        logger.debug("-Dio.netty.native.deleteLibAfterLoading: {}", DELETE_NATIVE_LIB_AFTER_LOADING)

        TRY_TO_PATCH_SHADED_ID = SystemPropertyUtil.getBoolean(
            "io.netty.native.tryPatchShadedId", true
        )
        logger.debug("-Dio.netty.native.tryPatchShadedId: {}", TRY_TO_PATCH_SHADED_ID)

        DETECT_NATIVE_LIBRARY_DUPLICATES = SystemPropertyUtil.getBoolean(
            "io.netty.native.detectNativeLibraryDuplicates", true
        )
        logger.debug("-Dio.netty.native.detectNativeLibraryDuplicates: {}", DETECT_NATIVE_LIBRARY_DUPLICATES)
    }

    /**
     * Loads the first available library in the collection with the specified
     * [ClassLoader].
     *
     * @throws IllegalArgumentException
     *         if none of the given libraries load successfully.
     */
    @JvmStatic
    fun loadFirstAvailable(loader: ClassLoader?, vararg names: String) {
        val suppressed = ArrayList<Throwable>()
        for (name in names) {
            try {
                load(name, loader)
                logger.debug("Loaded library with name '{}'", name)
                return
            } catch (t: Throwable) {
                suppressed.add(t)
            }
        }

        val iae = IllegalArgumentException(
            "Failed to load any of the given libraries: ${Arrays.toString(names)}"
        )
        ThrowableUtil.addSuppressedAndClear(iae, suppressed)
        throw iae
    }

    /**
     * Calculates the mangled shading prefix added to this class's full name.
     *
     * This method mangles the package name as follows, so we can unmangle it back later:
     * - `_` to `_1`
     * - `.` to `_`
     *
     * Note that we don't mangle non-ASCII characters here because it's extremely unlikely to have
     * a non-ASCII character in a package name. For more information, see:
     * - [JNI specification](https://docs.oracle.com/javase/8/docs/technotes/guides/jni/spec/design.html)
     * - `parsePackagePrefix()` in `netty_jni_util.c`.
     *
     * @throws UnsatisfiedLinkError if the shader used something other than a prefix
     */
    private fun calculateMangledPackagePrefix(): String {
        val maybeShaded = NativeLibraryLoader::class.java.name
        // Use ! instead of . to avoid shading utilities from modifying the string
        val expected = "io!netty!util!internal!NativeLibraryLoader".replace('!', '.')
        if (!maybeShaded.endsWith(expected)) {
            throw UnsatisfiedLinkError(
                String.format(
                    "Could not find prefix added to %s to get %s. When shading, only adding a " +
                        "package prefix is supported", expected, maybeShaded
                )
            )
        }
        return maybeShaded.substring(0, maybeShaded.length - expected.length)
            .replace("_", "_1")
            .replace('.', '_')
    }

    /**
     * Load the given library with the specified [ClassLoader]
     */
    @JvmStatic
    fun load(originalName: String, loader: ClassLoader?) {
        val mangledPackagePrefix = calculateMangledPackagePrefix()
        val name = mangledPackagePrefix + originalName
        val suppressed = ArrayList<Throwable>()
        try {
            // first try to load from java.library.path
            loadLibrary(loader, name, false)
            return
        } catch (ex: Throwable) {
            suppressed.add(ex)
        }

        val libname = System.mapLibraryName(name)
        val path = NATIVE_RESOURCE_HOME + libname

        var tmpFile: File? = null
        var url = getResource(path, loader)
        try {
            if (url == null) {
                if (PlatformDependent.isOsx()) {
                    val fileName = if (path.endsWith(".jnilib")) {
                        NATIVE_RESOURCE_HOME + "lib" + name + ".dynlib"
                    } else {
                        NATIVE_RESOURCE_HOME + "lib" + name + ".jnilib"
                    }
                    url = getResource(fileName, loader)
                    if (url == null) {
                        val fnf = FileNotFoundException(fileName)
                        ThrowableUtil.addSuppressedAndClear(fnf, suppressed)
                        throw fnf
                    }
                } else {
                    val fnf = FileNotFoundException(path)
                    ThrowableUtil.addSuppressedAndClear(fnf, suppressed)
                    throw fnf
                }
            }

            val index = libname.lastIndexOf('.')
            val prefix = libname.substring(0, index)
            val suffix = libname.substring(index)

            tmpFile = PlatformDependent.createTempFile(prefix, suffix, WORKDIR)
            url.openStream().use { `in` ->
                FileOutputStream(tmpFile).use { out ->
                    val buffer = ByteArray(8192)
                    var length: Int
                    while (`in`.read(buffer).also { length = it } > 0) {
                        out.write(buffer, 0, length)
                    }
                    out.flush()

                    if (shouldShadedLibraryIdBePatched(mangledPackagePrefix)) {
                        // Let's try to patch the id and re-sign it. This is a best-effort and might fail if a
                        // SecurityManager is setup or the right executables are not installed :/
                        tryPatchShadedLibraryIdAndSign(tmpFile, originalName)
                    }
                }
            }
            // Close the output stream before loading the unpacked library,
            // because otherwise Windows will refuse to load it when it's in use by other process.
            loadLibrary(loader, tmpFile.path, true)

        } catch (e: UnsatisfiedLinkError) {
            try {
                if (tmpFile != null && tmpFile.isFile && tmpFile.canRead() &&
                    !NoexecVolumeDetector.canExecuteExecutable(tmpFile)
                ) {
                    val message = String.format(
                        "%s exists but cannot be executed even when execute permissions set; " +
                            "check volume for \"noexec\" flag; use -D%s=[path] " +
                            "to set native working directory separately.",
                        tmpFile.path, "io.netty.native.workdir"
                    )
                    logger.info(message)
                    suppressed.add(
                        ThrowableUtil.unknownStackTrace(
                            UnsatisfiedLinkError(message), NativeLibraryLoader::class.java, "load"
                        )
                    )
                }
            } catch (t: Throwable) {
                suppressed.add(t)
                logger.debug("Error checking if {} is on a file store mounted with noexec", tmpFile ?: "null", t)
            }
            // Re-throw to fail the load
            ThrowableUtil.addSuppressedAndClear(e, suppressed)
            throw e
        } catch (e: Exception) {
            val ule = UnsatisfiedLinkError("could not load a native library: $name")
            ule.initCause(e)
            ThrowableUtil.addSuppressedAndClear(ule, suppressed)
            throw ule
        } finally {
            // After we load the library it is safe to delete the file.
            // We delete the file immediately to free up resources as soon as possible,
            // and if this fails fallback to deleting on JVM exit.
            if (tmpFile != null && (!DELETE_NATIVE_LIB_AFTER_LOADING || !tmpFile.delete())) {
                tmpFile.deleteOnExit()
            }
        }
    }

    private fun getResource(path: String, loader: ClassLoader?): URL? {
        val urls = try {
            if (loader == null) {
                ClassLoader.getSystemResources(path)
            } else {
                loader.getResources(path)
            }
        } catch (iox: IOException) {
            throw RuntimeException("An error occurred while getting the resources for $path", iox)
        }

        val urlsList = Collections.list(urls)
        val size = urlsList.size
        return when (size) {
            0 -> null
            1 -> urlsList[0]
            else -> {
                if (DETECT_NATIVE_LIBRARY_DUPLICATES) {
                    try {
                        val md = MessageDigest.getInstance("SHA-256")
                        val url = urlsList[0]
                        val digest = digest(md, url)
                        var allSame = true
                        if (digest != null) {
                            for (i in 1 until size) {
                                val digest2 = digest(md, urlsList[i])
                                if (digest2 == null || !Arrays.equals(digest, digest2)) {
                                    allSame = false
                                    break
                                }
                            }
                        } else {
                            allSame = false
                        }
                        if (allSame) {
                            return url
                        }
                    } catch (e: NoSuchAlgorithmException) {
                        logger.debug("Don't support SHA-256, can't check if resources have same content.", e)
                    }

                    throw IllegalStateException(
                        "Multiple resources found for '$path' with different content: $urlsList"
                    )
                } else {
                    logger.warn(
                        "Multiple resources found for '$path' with different content: " +
                            "$urlsList. Please fix your dependency graph."
                    )
                    urlsList[0]
                }
            }
        }
    }

    private fun digest(digest: MessageDigest, url: URL): ByteArray? {
        try {
            url.openStream().use { `in` ->
                val bytes = ByteArray(8192)
                var i: Int
                while (`in`.read(bytes).also { i = it } != -1) {
                    digest.update(bytes, 0, i)
                }
                return digest.digest()
            }
        } catch (e: IOException) {
            logger.debug("Can't read resource.", e)
            return null
        }
    }

    @JvmStatic
    fun tryPatchShadedLibraryIdAndSign(libraryFile: File, originalName: String) {
        if (!File("/Library/Developer/CommandLineTools").exists()) {
            logger.debug(
                "Can't patch shaded library id as CommandLineTools are not installed." +
                    " Consider installing CommandLineTools with 'xcode-select --install'"
            )
            return
        }
        val newId = String(generateUniqueId(originalName.length), CharsetUtil.UTF_8)
        if (!tryExec("install_name_tool -id $newId ${libraryFile.absolutePath}")) {
            return
        }

        tryExec("codesign -s - ${libraryFile.absolutePath}")
    }

    private fun tryExec(cmd: String): Boolean {
        try {
            @Suppress("DEPRECATION")
            val exitValue = Runtime.getRuntime().exec(cmd).waitFor()
            if (exitValue != 0) {
                logger.debug("Execution of '{}' failed: {}", cmd, exitValue)
                return false
            }
            logger.debug("Execution of '{}' succeed: {}", cmd, exitValue)
            return true
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        } catch (e: IOException) {
            logger.info("Execution of '{}' failed.", cmd, e)
        } catch (e: SecurityException) {
            logger.error("Execution of '{}' failed.", cmd, e)
        }
        return false
    }

    private fun shouldShadedLibraryIdBePatched(packagePrefix: String): Boolean {
        return TRY_TO_PATCH_SHADED_ID && PlatformDependent.isOsx() && packagePrefix.isNotEmpty()
    }

    private fun generateUniqueId(length: Int): ByteArray {
        val idBytes = ByteArray(length)
        for (i in idBytes.indices) {
            idBytes[i] = UNIQUE_ID_BYTES[ThreadLocalRandom.current()
                .nextInt(UNIQUE_ID_BYTES.size)]
        }
        return idBytes
    }

    /**
     * Loading the native library into the specified [ClassLoader].
     * @param loader The [ClassLoader] where the native library will be loaded into
     * @param name The native library path or name
     * @param absolute Whether the native library will be loaded by path or by name
     */
    private fun loadLibrary(loader: ClassLoader?, name: String, absolute: Boolean) {
        var suppressed: Throwable? = null
        try {
            try {
                // Make sure the helper belongs to the target ClassLoader.
                val newHelper = tryToLoadClass(loader, NativeLibraryUtil::class.java)
                loadLibraryByHelper(newHelper, name, absolute)
                logger.debug("Successfully loaded the library {}", name)
                return
            } catch (e: UnsatisfiedLinkError) { // Should by pass the UnsatisfiedLinkError here!
                suppressed = e
            } catch (e: Exception) {
                suppressed = e
            }
            NativeLibraryUtil.loadLibrary(name, absolute) // Fallback to local helper class.
            logger.debug("Successfully loaded the library {}", name)
        } catch (nsme: NoSuchMethodError) {
            if (suppressed != null) {
                ThrowableUtil.addSuppressed(nsme, suppressed)
            }
            throw LinkageError(
                "Possible multiple incompatible native libraries on the classpath for '$name'?", nsme
            )
        } catch (ule: UnsatisfiedLinkError) {
            if (suppressed != null) {
                ThrowableUtil.addSuppressed(ule, suppressed)
            }
            throw ule
        }
    }

    @Throws(UnsatisfiedLinkError::class)
    private fun loadLibraryByHelper(helper: Class<*>, name: String, absolute: Boolean) {
        val ret = AccessController.doPrivileged(PrivilegedAction<Any?> {
            try {
                val method = helper.getMethod("loadLibrary", String::class.java, Boolean::class.javaPrimitiveType)
                method.isAccessible = true
                return@PrivilegedAction method.invoke(null, name, absolute)
            } catch (e: Exception) {
                return@PrivilegedAction e
            }
        })
        if (ret is Throwable) {
            assert(ret !is UnsatisfiedLinkError) { "$ret should be a wrapper throwable" }
            val cause = ret.cause
            if (cause is UnsatisfiedLinkError) {
                throw cause
            }
            val ule = UnsatisfiedLinkError(ret.message)
            ule.initCause(ret)
            throw ule
        }
    }

    /**
     * Try to load the helper [Class] into specified [ClassLoader].
     * @param loader The [ClassLoader] where to load the helper [Class]
     * @param helper The helper [Class]
     * @return A new helper Class defined in the specified ClassLoader.
     * @throws ClassNotFoundException Helper class not found or loading failed
     */
    @Throws(ClassNotFoundException::class)
    private fun tryToLoadClass(loader: ClassLoader?, helper: Class<*>): Class<*> {
        try {
            return Class.forName(helper.name, false, loader)
        } catch (e1: ClassNotFoundException) {
            if (loader == null) {
                // cannot defineClass inside bootstrap class loader
                throw e1
            }
            try {
                val classBinary = classToByteArray(helper)
                return AccessController.doPrivileged(PrivilegedAction<Class<*>> {
                    try {
                        val defineClass = ClassLoader::class.java.getDeclaredMethod(
                            "defineClass", String::class.java,
                            ByteArray::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType
                        )
                        defineClass.isAccessible = true
                        return@PrivilegedAction defineClass.invoke(
                            loader, helper.name, classBinary, 0,
                            classBinary.size
                        ) as Class<*>
                    } catch (e: Exception) {
                        throw IllegalStateException("Define class failed!", e)
                    }
                })
            } catch (e2: ClassNotFoundException) {
                ThrowableUtil.addSuppressed(e2, e1)
                throw e2
            } catch (e2: RuntimeException) {
                ThrowableUtil.addSuppressed(e2, e1)
                throw e2
            } catch (e2: Error) {
                ThrowableUtil.addSuppressed(e2, e1)
                throw e2
            }
        }
    }

    /**
     * Load the helper [Class] as a byte array, to be redefined in specified [ClassLoader].
     * @param clazz The helper [Class] provided by this bundle
     * @return The binary content of helper [Class].
     * @throws ClassNotFoundException Helper class not found or loading failed
     */
    @Throws(ClassNotFoundException::class)
    private fun classToByteArray(clazz: Class<*>): ByteArray {
        var fileName = clazz.name
        val lastDot = fileName.lastIndexOf('.')
        if (lastDot > 0) {
            fileName = fileName.substring(lastDot + 1)
        }
        val classUrl = clazz.getResource("$fileName.class")
            ?: throw ClassNotFoundException(clazz.name)
        val buf = ByteArray(1024)
        val out = ByteArrayOutputStream(4096)
        try {
            classUrl.openStream().use { `in` ->
                var r: Int
                while (`in`.read(buf).also { r = it } != -1) {
                    out.write(buf, 0, r)
                }
                return out.toByteArray()
            }
        } catch (ex: IOException) {
            throw ClassNotFoundException(clazz.name, ex)
        }
    }

    private object NoexecVolumeDetector {

        @Throws(IOException::class)
        fun canExecuteExecutable(file: File): Boolean {
            if (file.canExecute()) {
                return true
            }

            val existingFilePermissions = Files.getPosixFilePermissions(file.toPath())
            val executePermissions = EnumSet.of(
                PosixFilePermission.OWNER_EXECUTE,
                PosixFilePermission.GROUP_EXECUTE,
                PosixFilePermission.OTHERS_EXECUTE
            )
            if (existingFilePermissions.containsAll(executePermissions)) {
                return false
            }

            val newPermissions = EnumSet.copyOf(existingFilePermissions)
            newPermissions.addAll(executePermissions)
            Files.setPosixFilePermissions(file.toPath(), newPermissions)
            return file.canExecute()
        }
    }
}
