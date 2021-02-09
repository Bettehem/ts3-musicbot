package ts3_musicbot.util

/*
    Copyright 2016  Chris Mustola
    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.
    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.
    You should have received a copy of the GNU General Public License
    along with this program.  If not, see http://www.gnu.org/licenses/.
 */

import java.util.*

class Time : Comparable<Time> {
    private lateinit var year: String
    private lateinit var month: String
    private lateinit var date: String
    lateinit var hour: String
    lateinit var minute: String
    lateinit var second: String

    private val timeInMillis: Long
        get() {
            val calendar = Calendar.getInstance()
            calendar.set(Integer.parseInt(year), Integer.parseInt(month), Integer.parseInt(date), Integer.parseInt(hour), Integer.parseInt(minute), Integer.parseInt(second))
            return calendar.timeInMillis
        }

    constructor(date: Calendar) {
        year = checkValue(date.get(Calendar.YEAR))
        month = checkValue(date.get(Calendar.MONTH) + 1)
        this.date = checkValue(date.get(Calendar.DATE))
        hour = checkValue(date.get(Calendar.HOUR_OF_DAY))
        minute = checkValue(date.get(Calendar.MINUTE))
        second = checkValue(date.get(Calendar.SECOND))
    }

    constructor(year: Int, month: Int, date: Int, hour: Int, minute: Int, second: Int) {
        this.year = checkValue(year)
        this.month = checkValue(month)
        this.date = checkValue(date)
        this.hour = checkValue(hour)
        this.minute = checkValue(minute)
        this.second = checkValue(second)
    }

    constructor(date: Date) {
        val calendar = Calendar.getInstance()
        calendar.time = date
        year = checkValue(calendar.get(Calendar.YEAR))
        month = checkValue(calendar.get(Calendar.MONTH) + 1)
        this.date = checkValue(calendar.get(Calendar.DATE))
        hour = checkValue(calendar.get(Calendar.HOUR_OF_DAY))
        minute = checkValue(calendar.get(Calendar.MINUTE))
        second = checkValue(calendar.get(Calendar.SECOND))
    }

    constructor(milliseconds: Long) {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = milliseconds
    }

    private fun checkValue(date: Int): String {
        return if (date < 10) {
            "0$date"
        } else {
            date.toString()
        }
    }

    override fun compareTo(other: Time): Int {
        //create Calendar object
        val calendar = Calendar.getInstance()
        calendar.set(Integer.parseInt(other.year), Integer.parseInt(other.month), Integer.parseInt(other.date), Integer.parseInt(other.hour), Integer.parseInt(other.minute), Integer.parseInt(other.second))

        //get the time in milliseconds
        val timeInMillis = timeInMillis

        //get the comparable time in milliseconds
        val anotherTimeInMillis = other.timeInMillis
        if (timeInMillis > anotherTimeInMillis) {
            return 1
        }
        return if (timeInMillis == anotherTimeInMillis) {
            0
        } else -1
    }
}
