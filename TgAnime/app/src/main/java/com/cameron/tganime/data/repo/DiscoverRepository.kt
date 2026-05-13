package com.cameron.tganime.data.repo

import com.cameron.tganime.data.network.BgmApi
import com.cameron.tganime.data.network.BgmCalendarDay

class DiscoverRepository(private val api: BgmApi) {
    suspend fun loadCalendar(): List<BgmCalendarDay> = api.calendar()
}
