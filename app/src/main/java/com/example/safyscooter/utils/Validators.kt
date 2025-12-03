package com.example.safyscooter.utils

object Validators {

    // Проверяем, что телефон введён корректно
    fun validateRussianPhone(phone: String): Boolean {

        // Формат должен быть строго: +7XXXXXXXXXX (12 символов)
        if (phone.length != 12 || !phone.startsWith("+7")) {
            return false
        }

        // Берём только цифры после +7
        val digitsOnly = phone.substring(2)

        // Проверяем, что каждая оставшаяся часть — это цифра
        for (char in digitsOnly) {
            if (!char.isDigit()) {
                return false
            }
        }

        // Если всё прошло — номер правильный
        return true
    }

    // Проверяем, что пароль подходит под наши правила
    fun validatePassword(password: String): Boolean {

        // Пароль должен быть от 8 до 16 символов
        if (password.length < 8 || password.length > 16) {
            return false
        }

        // Разрешаем только латинские буквы и цифры
        for (char in password) {
            if (char.isDigit() || char.isLatinLetter()) {
                continue // символ подходит
            } else {
                return false // если нашёлся запрещённый символ → пароль неверный
            }
        }

        // Пароль корректный
        return true
    }

    // Проверяем, что символ — латинская буква (A-Z или a-z)
    fun Char.isLatinLetter(): Boolean {
        return this in 'a'..'z' || this in 'A'..'Z'
    }
}
