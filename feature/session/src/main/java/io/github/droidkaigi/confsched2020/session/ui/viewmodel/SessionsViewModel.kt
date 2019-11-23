package io.github.droidkaigi.confsched2020.session.ui.viewmodel

import androidx.annotation.CheckResult
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.liveData
import io.github.droidkaigi.confsched2020.data.repository.SessionRepository
import io.github.droidkaigi.confsched2020.ext.LifecycleRunnable
import io.github.droidkaigi.confsched2020.ext.asLiveData
import io.github.droidkaigi.confsched2020.ext.composeBy
import io.github.droidkaigi.confsched2020.ext.onChanged
import io.github.droidkaigi.confsched2020.ext.requireValue
import io.github.droidkaigi.confsched2020.ext.toAppError
import io.github.droidkaigi.confsched2020.ext.toLoadingState
import io.github.droidkaigi.confsched2020.model.AppError
import io.github.droidkaigi.confsched2020.model.AudienceCategory
import io.github.droidkaigi.confsched2020.model.Category
import io.github.droidkaigi.confsched2020.model.Filters
import io.github.droidkaigi.confsched2020.model.Lang
import io.github.droidkaigi.confsched2020.model.LangSupport
import io.github.droidkaigi.confsched2020.model.LoadState
import io.github.droidkaigi.confsched2020.model.LoadingState
import io.github.droidkaigi.confsched2020.model.Room
import io.github.droidkaigi.confsched2020.model.Session
import io.github.droidkaigi.confsched2020.model.SessionContents
import io.github.droidkaigi.confsched2020.model.SessionPage
import javax.inject.Inject

class SessionsViewModel @Inject constructor(
    private val sessionRepository: SessionRepository
) : ViewModel() {
    // UiModel definition
    data class UiModel(
        val isLoading: Boolean,
        val error: AppError?,
        val filters: Filters,
        val allFilters: Filters,
        val dayToSessionsMap: Map<SessionPage.Day, List<Session>>,
        val favoritedSessions: List<Session>
    ) {
        companion object {
            val EMPTY = UiModel(false, null, Filters(), Filters(), mapOf(), listOf())
        }
    }

    // LiveDatas
    private val sessionsLoadStateLiveData: LiveData<LoadState<SessionContents>> = liveData {
        emitSource(
            sessionRepository.sessionContents()
                .toLoadingState()
                .asLiveData()
        )
        try {
            sessionRepository.refresh()
        } catch (ignored: Exception) {
            // We can show sessions with cache
        }
    }
    private val favoriteLoadingStateLiveData: MutableLiveData<LoadingState> =
        MutableLiveData(LoadingState.Initialized)

    private val filterLiveData: MutableLiveData<Filters> = MutableLiveData(Filters())

    // Compose UiModel
    val uiModel: LiveData<UiModel> = composeBy(
        initialValue = UiModel.EMPTY,
        liveData1 = sessionsLoadStateLiveData,
        liveData2 = favoriteLoadingStateLiveData,
        liveData3 = filterLiveData
    ) { current: UiModel,
        sessionsLoadState: LoadState<SessionContents>,
        favoriteLoadingState: LoadingState,
        filters: Filters
        ->
        val isLoading = sessionsLoadState.isLoading || favoriteLoadingState.isLoading
        val sessionContents = when (sessionsLoadState) {
            is LoadState.Loaded -> {
                sessionsLoadState.value
            }
            else -> {
                SessionContents.EMPTY
            }
        }
        val filteredSessions = sessionContents
            .sessions
            .filter { filters.isPass(it) }
        UiModel(
            isLoading = isLoading,
            error = (sessionsLoadState.getExceptionIfExists()
                ?: favoriteLoadingState.getExceptionIfExists()).toAppError(),
            filters = filters,
            allFilters = Filters(
                rooms = sessionContents.rooms.toSet(),
                audienceCategories = sessionContents.audienceCategories.toSet(),
                categories = sessionContents.category.toSet(),
                langs = sessionContents.langs.toSet(),
                langSupports = sessionContents.langSupports.toSet()
            ),
            dayToSessionsMap = filteredSessions
                .groupBy { it.dayNumber }
                .mapKeys {
                    SessionPage.dayOfNumber(
                        it.key
                    )
                },
            favoritedSessions = filteredSessions
                .filter { it.isFavorited }
        )
    }

    // Functions
    @CheckResult
    fun favorite(session: Session): LifecycleRunnable {
        return liveData {
            try {
                emit(LoadingState.Loading)
                sessionRepository.toggleFavorite(session)
                emit(LoadingState.Loaded)
            } catch (e: Exception) {
                emit(LoadingState.Error(e))
            }
        }.onChanged {
            favoriteLoadingStateLiveData.value = it
        }
    }

    fun filterChanged(room: Room, checked: Boolean) {
        val filters = filterLiveData.requireValue()
        filterLiveData.value = filters.copy(
            rooms = if (checked) filters.rooms + room else filters.rooms - room
        )
    }

    fun filterChanged(category: Category, checked: Boolean) {
        val filters = filterLiveData.requireValue()
        filterLiveData.value = filters.copy(
            categories = if (checked) filters.categories + category else filters.categories - category
        )
    }

    fun filterChanged(lang: Lang, checked: Boolean) {
        val filters = filterLiveData.requireValue()
        filterLiveData.value = filters.copy(
            langs = if (checked) filters.langs + lang else filters.langs - lang
        )
    }

    fun filterChanged(langSupport: LangSupport, checked: Boolean) {
        val filters = filterLiveData.requireValue()
        filterLiveData.value = filters.copy(
            langSupports = if (checked) filters.langSupports + langSupport else filters.langSupports - langSupport
        )
    }

    fun filterChanged(audienceCategory: AudienceCategory, checked: Boolean) {
        val filters = filterLiveData.requireValue()
        filterLiveData.value = filters.copy(
            audienceCategories = if (checked) filters.audienceCategories + audienceCategory else filters.audienceCategories - audienceCategory
        )
    }
}
