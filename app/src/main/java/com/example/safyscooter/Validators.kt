package com.example.safyscooter

object Validators {
    fun validateRussianPhone(phone: String): Boolean {
        if (phone.length != 12 || !phone.startsWith("+7")) {
            return false
        }

        val digitsOnly = phone.substring(2)

        for (char in digitsOnly) {
            if (!char.isDigit()) {
                return false
            }
        }
        return true
    }


    fun validatePassword(password: String): Boolean {
        if ((password.length < 8) || (password.length > 16)) {
            return false
        }

        for (char in password) {
            if (char.isDigit() || char.isLatinLetter()) {
                continue
            } else {
                return false
            }
        }
        return true
    }


    fun Char.isLatinLetter(): Boolean {
        return this in 'a'..'z' || this in 'A'..'Z'
    }
}


