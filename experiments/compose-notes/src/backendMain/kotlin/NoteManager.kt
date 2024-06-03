package io.deepmedia.tools.knee.sample

import io.deepmedia.tools.knee.annotations.*


@KneeClass class NoteManager @Knee constructor() {

    private val notes = mutableListOf(*FakeNotes.sortedBy { it.date }.toTypedArray())
    private val callbacks = mutableListOf<Callback>()

    @Knee fun addNote(note: Note) {
        if (notes.add(note)) {
            callbacks.forEach { it.onNoteAdded(note) }
        }
    }

    @Knee fun removeNote(id: String) {
        notes.filter { it.id == id }.forEach { note ->
            notes.remove(note)
            callbacks.forEach { it.onNoteRemoved(note) }
        }
    }

    @Knee val current: List<Note> get() = notes

    @Knee val size get() = notes.size

    @Knee fun registerCallback(callback: Callback) {
        callbacks.add(callback)
    }

    @Knee fun unregisterCallback(callback: Callback) {
        callbacks.remove(callback)
    }

    @KneeInterface
    interface Callback {
        fun onNoteAdded(note: Note)
        fun onNoteRemoved(note: Note)
    }
}