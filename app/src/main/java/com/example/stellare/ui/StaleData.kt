package com.example.stellare.ui

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update

/**
 * Jeden komunikat może pokrywać kilka strumieni, więc stan trzyma się per strumień: gdyby był
 * jedną flagą, strumień wygaszony rolą (emituje pustą listę od razu) zgasiłby komunikat o tym
 * strumieniu, który naprawdę padł. [retry] zamawia tylko nowy nasłuch — komunikat gaśnie dopiero
 * wtedy, gdy dane znów napłyną, bo inaczej samo kliknięcie przedstawiałoby starą migawkę
 * jako aktualną.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StaleData(private val staleMessage: String) {

    private val failingStreams = MutableStateFlow<Set<String>>(emptySet())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val reloads = MutableStateFlow(0)

    fun retry() {
        reloads.value += 1
    }

    /**
     * Wpis żyje dokładnie tyle, co zbieranie **całości** — dlatego `finally` obejmuje
     * [flatMapLatest], a nie pojedynczy nasłuch w środku. Objęcie środka myliłoby dwie rzeczy:
     * [retry] anuluje poprzedni nasłuch, żeby założyć nowy, i zdjęcie wpisu w tej chwili gasiłoby
     * komunikat, choć nikt jeszcze niczego nie potwierdził. Samo zakończenie nasłuchu też nie
     * zdejmuje wpisu, bo po odmowie trwałej `retryWithBackoff` kończy przepływ normalnie, a ekran
     * nadal pokazuje te dane. `reloads` jest stanem, więc ten przepływ kończy się wyłącznie wtedy,
     * gdy zbierający odejdzie — i wtedy wpis musi zniknąć, inaczej zostaje komunikat na
     * współdzielonej instancji z przyciskiem, który nie ma już czego ponowić.
     */
    fun <T> cover(tag: String, what: String, stream: () -> Flow<T>): Flow<T> = flow {
        try {
            emitAll(
                reloads.flatMapLatest {
                    stream()
                        .onEach { mark(what, failing = false) }
                        .retryWithBackoff(tag, what) { mark(what, failing = true) }
                }
            )
        } finally {
            mark(what, failing = false)
        }
    }

    private fun mark(what: String, failing: Boolean) {
        failingStreams.update { if (failing) it + what else it - what }
        _message.value = if (failingStreams.value.isEmpty()) null else staleMessage
    }
}
