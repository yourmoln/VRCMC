package com.vrcmc.app

import com.atilika.kuromoji.dict.CharacterDefinitions
import com.atilika.kuromoji.dict.ConnectionCosts
import com.atilika.kuromoji.dict.InsertedDictionary
import com.atilika.kuromoji.dict.TokenInfoDictionary
import com.atilika.kuromoji.dict.UnknownDictionary
import com.atilika.kuromoji.ipadic.Tokenizer
import com.atilika.kuromoji.trie.DoubleArrayTrie
import com.atilika.kuromoji.util.ResourceResolver
import com.atilika.kuromoji.util.SimpleResourceResolver
import dev.esnault.wanakana.core.Wanakana
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.zip.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val ipadicFeatureCount = 9
private const val ipadicResourcePrefix = "com/atilika/kuromoji/ipadic/"

private val japaneseTokenizer: Tokenizer by lazy {
    ExternalDictionaryTokenizerBuilder(japaneseDictionaryResolver()).build()
}

internal actual fun platformJapaneseDictionaryRequired(): Boolean = true

internal actual suspend fun platformJapaneseDictionaryAvailable(): Boolean =
    withContext(Dispatchers.IO) {
        japaneseDictionaryFile()?.hasExpectedDictionaryHash() == true
    }

internal actual suspend fun platformCacheJapaneseDictionary(channel: ByteReadChannel) {
    withContext(Dispatchers.IO) {
        val target =
            requireNotNull(japaneseDictionaryFile()) {
                "Application storage is not initialized"
            }
        Files.createDirectories(target.parent)
        val temporary = Files.createTempFile(target.parent, "$japaneseDictionaryArchiveName.", ".tmp")
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            var written = 0L
            Files.newOutputStream(temporary).use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = channel.readAvailable(buffer, 0, buffer.size)
                    if (count < 0) break
                    if (count == 0) continue
                    output.write(buffer, 0, count)
                    digest.update(buffer, 0, count)
                    written += count
                }
            }
            require(written == japaneseDictionaryArchiveSize) {
                "Dictionary size mismatch: $written"
            }
            require(digest.digest().toHexString() == japaneseDictionarySha256) {
                "Dictionary checksum mismatch"
            }
            runCatching {
                    Files.move(
                        temporary,
                        target,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING,
                    )
                }
                .getOrElse {
                    Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING)
                }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }
}

internal actual fun platformJapaneseRubySegments(text: String): List<JapaneseRubySegment> =
    japaneseRubySegments(text, japaneseTokenizer)

internal fun japaneseRubySegmentsFromArchive(
    text: String,
    archivePath: String,
): List<JapaneseRubySegment> =
    japaneseRubySegments(
        text,
        ExternalDictionaryTokenizerBuilder(
                JarDictionaryResourceResolver(Paths.get(archivePath))
            )
            .build(),
    )

private fun japaneseRubySegments(
    text: String,
    tokenizer: Tokenizer,
): List<JapaneseRubySegment> =
    assembleJapaneseRubySegments(
        text = text,
        tokens = japaneseReadingTokens(text, tokenizer),
        romanizePronunciation = ::romanizeJapanesePronunciation,
    )

private fun japaneseDictionaryFile(): Path? =
    platformJapaneseDictionaryCachePath()?.let(Paths::get)

private fun Path.hasExpectedDictionaryHash(): Boolean {
    if (!Files.isRegularFile(this) || Files.size(this) != japaneseDictionaryArchiveSize) return false
    val digest = MessageDigest.getInstance("SHA-256")
    Files.newInputStream(this).use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
    }
    return digest.digest().toHexString() == japaneseDictionarySha256
}

private fun ByteArray.toHexString(): String =
    joinToString(separator = "") { byte ->
        (byte.toInt() and 0xff).toString(16).padStart(2, '0')
    }

private fun japaneseDictionaryResolver(): ResourceResolver {
    val archive = japaneseDictionaryFile()?.takeIf(Files::isRegularFile)
    return if (archive != null) {
        JarDictionaryResourceResolver(archive)
    } else {
        // Desktop tests supply the complete artifact on their classpath.
        SimpleResourceResolver(Tokenizer::class.java)
    }
}

private class JarDictionaryResourceResolver(
    private val archive: Path
) : ResourceResolver {
    override fun resolve(resourceName: String): InputStream {
        val bytes =
            ZipFile(archive.toFile()).use { zip ->
                val entry =
                    zip.getEntry(ipadicResourcePrefix + resourceName)
                        ?: error("Dictionary resource not found: $resourceName")
                zip.getInputStream(entry).use(InputStream::readBytes)
            }
        return ByteArrayInputStream(bytes)
    }
}

private class ExternalDictionaryTokenizerBuilder(
    private val dictionaryResolver: ResourceResolver
) : Tokenizer.Builder() {
    override fun loadDictionaries() {
        penalties = mutableListOf(2, 3_000, 7, 1_700)
        resolver = dictionaryResolver
        doubleArrayTrie = DoubleArrayTrie.newInstance(dictionaryResolver)
        connectionCosts = ConnectionCosts.newInstance(dictionaryResolver)
        tokenInfoDictionary = TokenInfoDictionary.newInstance(dictionaryResolver)
        characterDefinitions = CharacterDefinitions.newInstance(dictionaryResolver)
        unknownDictionary =
            UnknownDictionary.newInstance(
                dictionaryResolver,
                characterDefinitions,
                ipadicFeatureCount,
            )
        insertedDictionary = InsertedDictionary(ipadicFeatureCount)
    }
}

private fun japaneseReadingTokens(
    text: String,
    tokenizer: Tokenizer,
): List<JapaneseReadingToken> =
    tokenizer.tokenize(text).map { token ->
        val surface = token.surface
        val partOfSpeech1 = token.partOfSpeechLevel1
        val partOfSpeech2 = token.partOfSpeechLevel2
        val whitespace = surface.any(Char::isWhitespace)
        val punctuation = partOfSpeech1 == "記号" && !whitespace
        val pronunciation =
            when {
                whitespace || punctuation -> null
                surface == "を" && partOfSpeech1 == "助詞" -> "オ"
                else -> token.pronunciation?.takeUnless { it.isBlank() || it == "*" }
            }
        JapaneseReadingToken(
            position = token.position,
            surface = surface,
            pronunciation = pronunciation,
            joinsPrevious =
                partOfSpeech1 == "助動詞" ||
                    partOfSpeech2 == "接続助詞" ||
                    partOfSpeech2 == "接尾",
            joinsNext = partOfSpeech1 == "接頭詞" || partOfSpeech2 == "接頭",
            punctuation = punctuation,
            contractsFinalOu =
                surface == "う" && partOfSpeech1 == "助動詞",
        )
    }

private fun romanizeJapanesePronunciation(pronunciation: String): String {
    val placeholder = '\uE000'
    val converted = Wanakana.toRomaji(pronunciation.replace('ー', placeholder))
    return buildString(converted.length) {
        converted.forEach { char ->
            if (char != placeholder) {
                append(char)
            } else if (isNotEmpty()) {
                val macron =
                    when (last()) {
                        'a' -> 'ā'
                        'i' -> 'ī'
                        'u' -> 'ū'
                        'e' -> 'ē'
                        'o' -> 'ō'
                        else -> null
                    }
                if (macron != null) setCharAt(lastIndex, macron)
            }
        }
    }
}
