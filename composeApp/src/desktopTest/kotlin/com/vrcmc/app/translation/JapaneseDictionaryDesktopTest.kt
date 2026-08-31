package com.vrcmc.app

import com.atilika.kuromoji.ipadic.Tokenizer
import io.ktor.utils.io.ByteReadChannel
import java.net.JarURLConnection
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class JapaneseDictionaryDesktopTest {
    @Test
    fun downloadedArchiveIsVerifiedAndCachedAtomically() = runBlocking {
        val temporaryDirectory = Files.createTempDirectory("vrcmc-romaji-test")
        val target = temporaryDirectory.resolve(japaneseDictionaryArchiveName)
        System.setProperty("vrcmc.japaneseDictionary.path", target.toString())
        try {
            val archive = completeIpadicArchive()
            assertEquals(japaneseDictionaryArchiveSize, archive.size.toLong())

            platformCacheJapaneseDictionary(ByteReadChannel(archive))

            assertTrue(platformJapaneseDictionaryAvailable())
            assertEquals(japaneseDictionaryArchiveSize, Files.size(target))
            assertEquals(
                listOf(JapaneseRubySegment("東京", "tōkyō")),
                japaneseRubySegmentsFromArchive("東京", target.toString()),
            )

            Files.write(target, byteArrayOf(0))
            assertFalse(platformJapaneseDictionaryAvailable())
        } finally {
            System.clearProperty("vrcmc.japaneseDictionary.path")
            deleteRecursively(temporaryDirectory)
        }
    }

    private fun completeIpadicArchive(): ByteArray {
        val resource =
            requireNotNull(Tokenizer::class.java.getResource("tokenInfoDictionary.bin")) {
                "Complete IPADIC test dependency is missing"
            }
        val connection = resource.openConnection() as JarURLConnection
        return connection.jarFileURL.openStream().use { it.readBytes() }
    }

    private fun deleteRecursively(path: Path) {
        if (!Files.exists(path)) return
        Files.walk(path).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
    }
}
