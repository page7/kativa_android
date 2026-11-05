package com.kativa.reader.data.repository

import com.kativa.reader.core.network.ApiFactory
import com.kativa.reader.core.network.ErrorMapper
import com.kativa.reader.core.network.KativaFailure
import com.kativa.reader.core.network.Outcome
import com.kativa.reader.data.remote.dto.SeriesDto
import com.kativa.reader.data.remote.dto.SeriesFilterV2Dto
import javax.inject.Inject
import javax.inject.Singleton

/** M0 home data: the three provided streams plus the continue-reading entry. */
@Singleton
class SeriesRepository @Inject constructor(
    private val apiFactory: ApiFactory,
) {
    suspend fun onDeck(page: Int = 1, pageSize: Int = 20): Outcome<List<SeriesDto>> = run {
        try {
            Outcome.Success(apiFactory.series().onDeck(pageNumber = page, pageSize = pageSize))
        } catch (t: Throwable) {
            Outcome.Failure(KativaFailure(ErrorMapper.from(t)))
        }
    }

    suspend fun recentlyUpdated(page: Int = 1, pageSize: Int = 20): Outcome<List<com.kativa.reader.data.remote.dto.GroupedSeriesDto>> = run {
        try {
            Outcome.Success(apiFactory.series().recentlyUpdated(pageNumber = page, pageSize = pageSize))
        } catch (t: Throwable) {
            Outcome.Failure(KativaFailure(ErrorMapper.from(t)))
        }
    }

    suspend fun recentlyAdded(page: Int = 1, pageSize: Int = 20): Outcome<List<SeriesDto>> = run {
        try {
            Outcome.Success(apiFactory.series().recentlyAdded(pageNumber = page, pageSize = pageSize))
        } catch (t: Throwable) {
            Outcome.Failure(KativaFailure(ErrorMapper.from(t)))
        }
    }

    suspend fun series(seriesId: Int): Outcome<SeriesDto> = run {
        try {
            Outcome.Success(apiFactory.series().series(seriesId))
        } catch (t: Throwable) {
            Outcome.Failure(KativaFailure(ErrorMapper.from(t)))
        }
    }

    suspend fun libraries(): Outcome<List<com.kativa.reader.data.remote.api.LibraryDto>> = run {
        try {
            Outcome.Success(apiFactory.library().libraries())
        } catch (t: Throwable) {
            Outcome.Failure(KativaFailure(ErrorMapper.from(t)))
        }
    }

    suspend fun dashboardStreams(visibleOnly: Boolean = true): Outcome<List<com.kativa.reader.data.remote.dto.DashboardStreamDto>> = run {
        try {
            Outcome.Success(apiFactory.stream().dashboard(visibleOnly))
        } catch (t: Throwable) {
            Outcome.Failure(KativaFailure(ErrorMapper.from(t)))
        }
    }

    suspend fun progress(chapterId: Int): Outcome<com.kativa.reader.data.remote.dto.ProgressDto> = run {
        try {
            Outcome.Success(apiFactory.reader().progress(chapterId))
        } catch (t: Throwable) {
            Outcome.Failure(KativaFailure(ErrorMapper.from(t)))
        }
    }

    suspend fun emptyFilter(): SeriesFilterV2Dto = SeriesFilterV2Dto()
}
