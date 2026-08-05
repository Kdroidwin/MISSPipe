package org.schabi.newpipe.settings

import android.content.SharedPreferences
import android.util.Log
import org.schabi.newpipe.streams.io.SharpOutputStream
import org.schabi.newpipe.streams.io.StoredFileHelper
import org.schabi.newpipe.util.ZipHelper
import java.io.BufferedOutputStream
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.InvalidClassException
import java.io.IOException
import java.io.ObjectInputStream
import java.io.ObjectStreamClass
import java.nio.charset.StandardCharsets
import java.util.LinkedHashMap
import java.util.LinkedHashSet
import java.util.zip.ZipOutputStream

class ContentSettingsManager(private val fileLocator: NewPipeFileLocator) {
    companion object {
        const val TAG = "ContentSetManager"
        private const val SETTINGS_MAGIC = 0x4D495350 // MISP
        private const val SETTINGS_VERSION = 1
        private const val MAX_ENTRIES = 4096
        private const val MAX_STRING_BYTES = 1024 * 1024
        private const val MAX_SETTINGS_FILE_BYTES = 2L * 1024L * 1024L
        private const val TYPE_BOOLEAN = 1
        private const val TYPE_FLOAT = 2
        private const val TYPE_INT = 3
        private const val TYPE_LONG = 4
        private const val TYPE_STRING = 5
        private const val TYPE_STRING_SET = 6
    }

    /**
     * Exports given [SharedPreferences] to the file in given outputPath.
     * It also creates the file.
     */
    @Throws(Exception::class)
    fun exportDatabase(preferences: SharedPreferences, file: StoredFileHelper) {
        file.create()
        ZipOutputStream(BufferedOutputStream(SharpOutputStream(file.stream)))
            .use { outZip ->
                ZipHelper.addFileToZip(outZip, fileLocator.db.path, "newpipe.db")

                try {
                    writeSettings(preferences.all)
                } catch (e: IOException) {
                    Log.e(TAG, "Unable to exportDatabase", e)
                }

                ZipHelper.addFileToZip(outZip, fileLocator.settings.path, "newpipe.settings")
            }
    }

    fun deleteSettingsFile() {
        fileLocator.settings.delete()
    }

    /**
     * Tries to create database directory if it does not exist.
     *
     * @return Whether the directory exists afterwards.
     */
    fun ensureDbDirectoryExists(): Boolean {
        return fileLocator.dbDir.exists() || fileLocator.dbDir.mkdir()
    }

    fun extractDb(file: StoredFileHelper): Boolean {
        val success = ZipHelper.extractFileFromZip(file, fileLocator.db.path, "newpipe.db")
        if (success) {
            fileLocator.dbJournal.delete()
            fileLocator.dbWal.delete()
            fileLocator.dbShm.delete()
        }

        return success
    }

    fun extractSettings(file: StoredFileHelper): Boolean {
        return ZipHelper.extractFileFromZip(
            file, fileLocator.settings.path, "newpipe.settings", MAX_SETTINGS_FILE_BYTES
        )
    }

    fun loadSharedPreferences(preferences: SharedPreferences) {
        try {
            val entries = readSettings()
            val preferenceEditor = preferences.edit().clear()
            entries.forEach { (key, value) ->
                when (value) {
                    is Boolean -> preferenceEditor.putBoolean(key, value)
                    is Float -> preferenceEditor.putFloat(key, value)
                    is Int -> preferenceEditor.putInt(key, value)
                    is Long -> preferenceEditor.putLong(key, value)
                    is String -> preferenceEditor.putString(key, value)
                    is Set<*> -> preferenceEditor.putStringSet(key, value.filterIsInstance<String>().toSet())
                }
            }
            if (!preferenceEditor.commit()) {
                throw IOException("Unable to commit imported preferences")
            }
        } catch (e: IOException) {
            Log.e(TAG, "Unable to loadSharedPreferences", e)
        } catch (e: ClassNotFoundException) {
            Log.e(TAG, "Unable to loadSharedPreferences", e)
        }
    }

    @Throws(IOException::class)
    private fun writeSettings(entries: Map<String, *>) {
        val exportable = entries.filter { (key, value) -> isExportableKey(key) && isSupportedValue(value) }
        DataOutputStream(BufferedOutputStream(FileOutputStream(fileLocator.settings))).use { output ->
            output.writeInt(SETTINGS_MAGIC)
            output.writeInt(SETTINGS_VERSION)
            output.writeInt(exportable.size)
            exportable.forEach { (key, value) ->
                writeString(output, key)
                when (value) {
                    is Boolean -> { output.writeByte(TYPE_BOOLEAN); output.writeBoolean(value) }
                    is Float -> { output.writeByte(TYPE_FLOAT); output.writeFloat(value) }
                    is Int -> { output.writeByte(TYPE_INT); output.writeInt(value) }
                    is Long -> { output.writeByte(TYPE_LONG); output.writeLong(value) }
                    is String -> { output.writeByte(TYPE_STRING); writeString(output, value) }
                    is Set<*> -> {
                        output.writeByte(TYPE_STRING_SET)
                        val strings = value.filterIsInstance<String>()
                        output.writeInt(strings.size)
                        strings.forEach { writeString(output, it) }
                    }
                }
            }
        }
    }

    @Throws(IOException::class, ClassNotFoundException::class)
    private fun readSettings(): Map<String, Any> {
        BufferedInputStream(FileInputStream(fileLocator.settings)).use { input ->
            input.mark(8)
            val header = DataInputStream(input).readInt()
            input.reset()
            return if (header == SETTINGS_MAGIC) readCurrentSettings(input) else readLegacySettings(input)
        }
    }

    @Throws(IOException::class)
    private fun readCurrentSettings(input: InputStream): Map<String, Any> {
        val data = DataInputStream(input)
        if (data.readInt() != SETTINGS_MAGIC || data.readInt() != SETTINGS_VERSION) {
            throw IOException("Unsupported settings backup")
        }
        val count = data.readInt()
        if (count !in 0..MAX_ENTRIES) throw IOException("Invalid settings entry count")
        val entries = LinkedHashMap<String, Any>(count)
        repeat(count) {
            val key = readString(data)
            val value: Any = when (data.readUnsignedByte()) {
                TYPE_BOOLEAN -> data.readBoolean()
                TYPE_FLOAT -> data.readFloat()
                TYPE_INT -> data.readInt()
                TYPE_LONG -> data.readLong()
                TYPE_STRING -> readString(data)
                TYPE_STRING_SET -> {
                    val setSize = data.readInt()
                    if (setSize !in 0..MAX_ENTRIES) throw IOException("Invalid string set size")
                    LinkedHashSet<String>(setSize).apply { repeat(setSize) { add(readString(data)) } }
                }
                else -> throw IOException("Unsupported settings value type")
            }
            if (isExportableKey(key)) entries[key] = value
        }
        return entries
    }

    @Throws(IOException::class, ClassNotFoundException::class)
    private fun readLegacySettings(input: InputStream): Map<String, Any> {
        RestrictedObjectInputStream(input).use { objectInput ->
            val serialized = objectInput.readObject()
            if (serialized !is Map<*, *>) throw IOException("Invalid legacy settings backup")
            val entries = LinkedHashMap<String, Any>()
            serialized.forEach { (key, value) ->
                if (key is String && isExportableKey(key) && isSupportedValue(value)) entries[key] = value!!
            }
            return entries
        }
    }

    private fun writeString(output: DataOutputStream, value: String) {
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        require(bytes.size <= MAX_STRING_BYTES) { "Settings value exceeds size limit" }
        output.writeInt(bytes.size)
        output.write(bytes)
    }

    private fun readString(input: DataInputStream): String {
        val size = input.readInt()
        if (size !in 0..MAX_STRING_BYTES) throw IOException("Invalid settings string size")
        return ByteArray(size).also { input.readFully(it) }.toString(StandardCharsets.UTF_8)
    }

    private fun isExportableKey(key: String): Boolean {
        val normalized = key.lowercase()
        return !normalized.contains("_cookies_key")
                && !normalized.contains("_po_token_key")
                && !normalized.contains("override_cookies_")
                && !normalized.contains("recaptcha")
                && !normalized.contains("sabr_potoken")
    }

    private fun isSupportedValue(value: Any?): Boolean = when (value) {
        is Boolean, is Float, is Int, is Long, is String -> true
        is Set<*> -> value.all { it is String }
        else -> false
    }

    private class RestrictedObjectInputStream(input: InputStream) : ObjectInputStream(input) {
        override fun resolveClass(descriptor: ObjectStreamClass): Class<*> {
            val allowed = setOf(
                "java.util.HashMap", "java.util.LinkedHashMap", "java.util.HashSet",
                "java.util.LinkedHashSet", "java.lang.String", "java.lang.Boolean",
                "java.lang.Float", "java.lang.Integer", "java.lang.Long"
            )
            if (descriptor.name !in allowed) throw InvalidClassException("Unexpected class", descriptor.name)
            return super.resolveClass(descriptor)
        }
    }
}
