package com.steve.junker.data.api

import com.steve.junker.BuildConfig

object AirtableConfig {
    val BASE_ID: String = BuildConfig.AIRTABLE_BASE_ID.ifEmpty { "app3uUoCzkeAFm5Nv" }
    val PERSONAL_ACCESS_TOKEN: String = BuildConfig.AIRTABLE_TOKEN
}
