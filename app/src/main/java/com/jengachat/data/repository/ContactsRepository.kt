package com.jengachat.data.repository

import android.content.ContentResolver
import android.content.Context
import android.provider.ContactsContract
import com.jengachat.util.PermissionManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Data class representing a phone contact
 */
data class PhoneContact(
    val id: String,
    val name: String,
    val phoneNumber: String?,
    val email: String?,
    val photoUri: String?
)

/**
 * Repository for accessing phone contacts
 */
@Singleton
class ContactsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val contentResolver: ContentResolver = context.contentResolver

    /**
     * Get all phone contacts with email addresses
     * Returns empty list if permission is not granted
     */
    suspend fun getPhoneContacts(): List<PhoneContact> = withContext(Dispatchers.IO) {
        if (!PermissionManager.hasContactsPermission(context)) {
            return@withContext emptyList()
        }

        val contacts = mutableMapOf<String, PhoneContact>()
        
        // Query contacts with emails
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Email.CONTACT_ID,
            ContactsContract.CommonDataKinds.Email.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Email.ADDRESS,
            ContactsContract.CommonDataKinds.Email.PHOTO_URI
        )

        val cursor = contentResolver.query(
            ContactsContract.CommonDataKinds.Email.CONTENT_URI,
            projection,
            null,
            null,
            ContactsContract.CommonDataKinds.Email.DISPLAY_NAME + " ASC"
        )

        cursor?.use {
            val idIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Email.CONTACT_ID)
            val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Email.DISPLAY_NAME)
            val emailIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Email.ADDRESS)
            val photoIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Email.PHOTO_URI)

            while (it.moveToNext()) {
                val id = it.getString(idIndex) ?: continue
                val name = it.getString(nameIndex) ?: "Unknown"
                val email = it.getString(emailIndex)
                val photoUri = it.getString(photoIndex)

                if (!email.isNullOrBlank() && !contacts.containsKey(id)) {
                    contacts[id] = PhoneContact(
                        id = id,
                        name = name,
                        phoneNumber = null,
                        email = email.lowercase(),
                        photoUri = photoUri
                    )
                }
            }
        }

        // Also get phone numbers for contacts
        val phoneProjection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.PHOTO_URI
        )

        val phoneCursor = contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            phoneProjection,
            null,
            null,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
        )

        phoneCursor?.use {
            val idIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val phoneIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val photoIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)

            while (it.moveToNext()) {
                val id = it.getString(idIndex) ?: continue
                val name = it.getString(nameIndex) ?: "Unknown"
                val phone = it.getString(phoneIndex)
                val photoUri = it.getString(photoIndex)

                if (!phone.isNullOrBlank()) {
                    val existing = contacts[id]
                    if (existing != null) {
                        // Update with phone number
                        contacts[id] = existing.copy(phoneNumber = phone)
                    } else {
                        // Add new contact with phone only
                        contacts[id] = PhoneContact(
                            id = id,
                            name = name,
                            phoneNumber = phone,
                            email = null,
                            photoUri = photoUri
                        )
                    }
                }
            }
        }

        contacts.values.toList().sortedBy { it.name }
    }

    /**
     * Get contacts that have email addresses (for matching with Talksy users)
     */
    suspend fun getContactsWithEmails(): List<PhoneContact> {
        return getPhoneContacts().filter { it.email != null }
    }

    /**
     * Get list of all email addresses from contacts
     */
    suspend fun getContactEmails(): List<String> {
        return getContactsWithEmails().mapNotNull { it.email }
    }

    /**
     * Search contacts by name or email
     */
    suspend fun searchContacts(query: String): List<PhoneContact> {
        val lowerQuery = query.lowercase()
        return getPhoneContacts().filter { contact ->
            contact.name.lowercase().contains(lowerQuery) ||
            contact.email?.lowercase()?.contains(lowerQuery) == true ||
            contact.phoneNumber?.contains(query) == true
        }
    }
}
