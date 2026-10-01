package com.lucent.app.harness

import android.content.Context
import android.system.Os
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream

object BuiltinRuntimeInstaller {

    fun assetVersion(context: Context): Int = try {
        context.assets.open("runtime/rootfs-version.txt").bufferedReader().use {
            it.readText().trim().toInt()
        }
    } catch (_: Throwable) {
        0
    }

    suspend fun ensureInstalled(context: Context, onProgress: (String) -> Unit): ShellOutcome = withContext(Dispatchers.IO) {
        try {
            val targetVersion = assetVersion(context)
            val rootfsDir = BuiltinShell.rootfsDir(context)
            extractNativeLibs(context)
            val versionFile = File(rootfsDir, ".lucent-rootfs-version")
            val currentVersion = try {
                if (versionFile.exists()) versionFile.readText().trim().toInt() else -1
            } catch (_: Throwable) {
                -1
            }
            if (File(rootfsDir, "bin/bash").exists() && currentVersion >= targetVersion) {
                ensureHostIdentity(rootfsDir)
                return@withContext ShellOutcome(true, "built-in environment is ready", "", 0)
            }

            val parentDir = rootfsDir.parentFile ?: context.filesDir
            parentDir.mkdirs()
            val tmp = File(parentDir, "rootfs.tmp")
            if (tmp.exists()) tmp.deleteRecursively()
            tmp.mkdirs()

            onProgress("extracting the built-in environment")

            var count = 0
            var violations = 0
            var skipped = 0
            val canonicalTmp = tmp.canonicalPath
            context.assets.open("runtime/ubuntu-rootfs-arm64.tar.xz").use { rawIn ->
                XZCompressorInputStream(rawIn).use { xzIn ->
                    TarArchiveInputStream(xzIn).use { tarIn ->
                        while (true) {
                            val entry = tarIn.nextTarEntry ?: break
                            val destination = File(tmp, entry.name)
                            val canonicalDest = destination.canonicalPath
                            if (!canonicalDest.startsWith(canonicalTmp + File.separator) && canonicalDest != canonicalTmp) {
                                violations++
                                continue
                            }
                            if (entry.isBlockDevice || entry.isCharacterDevice || entry.isFIFO) {
                                continue
                            }
                            if (entry.isDirectory) {
                                destination.mkdirs()
                            } else if (entry.isSymbolicLink) {
                                if (destination.exists()) destination.delete()
                                destination.parentFile?.mkdirs()
                                Os.symlink(entry.linkName, destination.absolutePath)
                            } else if (entry.isLink) {
                                val linkTarget = File(tmp, entry.linkName)
                                if (!linkTarget.exists()) {
                                    skipped++
                                    continue
                                }
                                if (destination.exists()) destination.delete()
                                destination.parentFile?.mkdirs()
                                try {
                                    Os.link(linkTarget.absolutePath, destination.absolutePath)
                                } catch (_: Throwable) {
                                    linkTarget.copyTo(destination, overwrite = true)
                                }
                            } else if (entry.isFile) {
                                destination.parentFile?.mkdirs()
                                destination.outputStream().use { out ->
                                    val buf = ByteArray(8192)
                                    var r: Int
                                    while (tarIn.read(buf).also { r = it } != -1) {
                                        out.write(buf, 0, r)
                                    }
                                }
                            }
                            if (!entry.isSymbolicLink) {
                                try {
                                    Os.chmod(destination.absolutePath, entry.mode and 0xFFF)
                                } catch (_: Throwable) {
                                }
                            }
                            try {
                                destination.setLastModified(entry.modTime.time)
                            } catch (_: Throwable) {
                            }
                            count++
                            if (count % 200 == 0) {
                                onProgress("extracting environment ($count files)")
                            }
                        }
                    }
                }
            }

            val certDest = File(tmp, "etc/ssl/certs/ca-certificates.crt")
            certDest.parentFile?.mkdirs()
            context.assets.open("runtime/cacert.pem").use { certIn ->
                certDest.outputStream().use { certOut ->
                    certIn.copyTo(certOut)
                }
            }

            val resolvDest = File(tmp, "etc/resolv.conf")
            if (java.nio.file.Files.isSymbolicLink(resolvDest.toPath())) {
                resolvDest.delete()
            }
            resolvDest.parentFile?.mkdirs()
            resolvDest.writeText("nameserver 223.5.5.5\nnameserver 119.29.29.29\nnameserver 8.8.8.8\nnameserver 1.1.1.1\n")

            val bash = File(tmp, "bin/bash")
            val ld = File(tmp, "lib/ld-linux-aarch64.so.1")
            val osRel = File(tmp, "etc/os-release")
            if (!bash.exists() || !ld.exists() || !osRel.exists()) {
                tmp.deleteRecursively()
                return@withContext ShellOutcome(false, "", "Verification failed: required files missing from extracted rootfs", -1)
            }

            ensureHostIdentity(tmp)

            File(tmp, ".lucent-rootfs-version").writeText(targetVersion.toString())
            if (rootfsDir.exists()) {
                rootfsDir.deleteRecursively()
            }
            val renamed = tmp.renameTo(rootfsDir)
            if (!renamed) {
                tmp.copyRecursively(rootfsDir, overwrite = true)
                tmp.deleteRecursively()
            }

            ShellOutcome(true, "built-in environment is ready\nExtracted $count files, version $targetVersion", "", 0)
        } catch (t: Throwable) {
            ShellOutcome(false, "", t.message ?: t.toString(), -1)
        }
    }

    private fun ensureHostIdentity(rootfs: File) {
        val uid = Os.getuid()
        val gid = Os.getgid()
        val passwd = File(rootfs, "etc/passwd")
        val groups = File(rootfs, "etc/group")
        val passwdLines = passwd.readLines().toMutableList()
        if (passwdLines.none { it.split(':').getOrNull(2)?.toIntOrNull() == uid }) {
            passwdLines.add("android_$uid:x:$uid:$gid:Android app user:/root:/bin/bash")
            passwd.writeText(passwdLines.joinToString("\n", postfix = "\n"))
        }
        val groupLines = groups.readLines().toMutableList()
        (supplementaryGroups().toSet() + gid).filter { it >= 0 }.forEach { groupId ->
            if (groupLines.none { it.split(':').getOrNull(2)?.toIntOrNull() == groupId }) {
                groupLines.add("android_$groupId:x:$groupId:")
            }
        }
        groups.writeText(groupLines.joinToString("\n", postfix = "\n"))
    }

    private fun supplementaryGroups(): List<Int> {
        val groupsLine = try {
            File("/proc/self/status").bufferedReader().useLines { lines ->
                lines.firstOrNull { it.startsWith("Groups:") }
            }
        } catch (_: Throwable) {
            null
        }
        if (groupsLine == null) return emptyList()
        return groupsLine.substringAfter("Groups:")
            .trim()
            .split(Regex("\\s+"))
            .mapNotNull { it.toIntOrNull() }
    }

    private fun extractNativeLibs(context: Context) {
        val nativeLibDir = File(context.filesDir, "native-lib")
        nativeLibDir.mkdirs()
        for (name in listOf("libtalloc.so.2", "libandroid-shmem.so")) {
            val dest = File(nativeLibDir, name)
            val assetPath = "runtime/native-libs/$name"
            val assetSize = try {
                val fd = context.assets.openFd(assetPath)
                val len = fd.length
                fd.close()
                if (len >= 0) len else null
            } catch (_: Throwable) {
                null
            } ?: context.assets.open(assetPath).use { stream ->
                var count = 0L
                val buf = ByteArray(8192)
                var r: Int
                while (stream.read(buf).also { r = it } != -1) {
                    count += r
                }
                count
            }
            if (!dest.exists() || dest.length() != assetSize) {
                context.assets.open(assetPath).use { input ->
                    dest.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                try {
                    Os.chmod(dest.absolutePath, 493)
                } catch (_: Throwable) {
                }
            }
        }
    }
}
