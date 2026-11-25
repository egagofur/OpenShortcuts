package app.openshortcuts.engine

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.Contacts as C
import android.provider.ContactsContract.Data

object Contacts {
    data class Person(
        val id: Long,
        val name: String,
        val phones: List<String>,
        val emails: List<String>,
        val whatsapp: Set<String>,
        val photo: Uri?,
    ) {
        fun hasWhatsApp(number: String) = digits(number).let { d -> whatsapp.any { it.endsWith(d.takeLast(9)) } }
    }

    private val WA_MIME = listOf(
        "vnd.android.cursor.item/vnd.com.whatsapp.profile",
        "vnd.android.cursor.item/vnd.com.whatsapp.w4b.profile",
    )

    fun allowed(ctx: Context) = ctx.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    fun digits(s: String) = s.filter(Char::isDigit)

    fun load(ctx: Context): List<Person> {
        if (!allowed(ctx)) return emptyList()
        val cr = ctx.contentResolver
        val names = HashMap<Long, Pair<String, Uri?>>()
        cr.query(C.CONTENT_URI, arrayOf(C._ID, C.DISPLAY_NAME_PRIMARY, C.PHOTO_THUMBNAIL_URI), null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val name = c.getString(1) ?: continue
                names[c.getLong(0)] = name to c.getString(2)?.let(Uri::parse)
            }
        }
        val phones = HashMap<Long, LinkedHashSet<String>>()
        cr.query(Phone.CONTENT_URI, arrayOf(Phone.CONTACT_ID, Phone.NUMBER), null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val n = c.getString(1)?.trim() ?: continue
                val set = phones.getOrPut(c.getLong(0)) { LinkedHashSet() }
                // The same number often appears twice (SIM and Google); keep the first spelling.
                if (set.none { digits(it).takeLast(9) == digits(n).takeLast(9) }) set += n
            }
        }
        val emails = HashMap<Long, LinkedHashSet<String>>()
        cr.query(Email.CONTENT_URI, arrayOf(Email.CONTACT_ID, Email.ADDRESS), null, null, null)?.use { c ->
            while (c.moveToNext()) c.getString(1)?.trim()?.let { emails.getOrPut(c.getLong(0)) { LinkedHashSet() } += it }
        }
        val wa = HashMap<Long, MutableSet<String>>()
        val marks = WA_MIME.joinToString(",") { "?" }
        cr.query(Data.CONTENT_URI, arrayOf(Data.CONTACT_ID, Data.DATA1), "${Data.MIMETYPE} IN ($marks)", WA_MIME.toTypedArray(), null)?.use { c ->
            while (c.moveToNext()) {
                val jid = c.getString(1) ?: continue // 6281234567890@s.whatsapp.net
                wa.getOrPut(c.getLong(0)) { mutableSetOf() } += digits(jid.substringBefore('@'))
            }
        }
        return names.map { (id, np) ->
            Person(id, np.first, phones[id].orEmpty().toList(), emails[id].orEmpty().toList(), wa[id].orEmpty(), np.second)
        }.filter { it.phones.isNotEmpty() || it.emails.isNotEmpty() }
            .sortedWith(compareBy<Person>({ it.name.firstOrNull()?.isLetter() != true }, { it.name.lowercase() }))
    }
}
