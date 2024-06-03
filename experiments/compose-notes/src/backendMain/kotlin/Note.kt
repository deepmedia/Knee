package io.deepmedia.tools.knee.sample

import io.deepmedia.tools.knee.annotations.*
import kotlinx.cinterop.UnsafeNumber
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import platform.posix.gettimeofday
import platform.posix.timeval
import kotlin.random.Random

@KneeClass
data class Note @Knee constructor(
    @Knee val id: String,
    @Knee val author: String,
    @Knee val date: Long,
    @Knee val content: String
)

private val FakeAuthors = listOf("Kate", "Emma", "John", "Mark", "Lucy", "Richard", "Joe")

private val FakeWords = """
    Lorem ipsum dolor sit amet, consectetur adipiscing elit. Vestibulum mattis massa non auctor sodales. Fusce mattis non erat quis euismod. Etiam suscipit enim sed luctus efficitur. Sed ultrices tincidunt maximus. Donec rutrum, dolor nec porta fringilla, arcu magna tincidunt dui, non sollicitudin est lectus id quam. Vestibulum sit amet suscipit diam. Sed et risus ut ex eleifend scelerisque facilisis sit amet metus. Morbi a erat mauris. Morbi et sem lacinia, sagittis eros et, sodales libero. Cras ac ligula in leo blandit scelerisque ac vitae nisl. Maecenas leo mi, fermentum nec ante sed, sagittis rutrum felis.
    Morbi ipsum tortor, dictum iaculis nisi et, egestas lacinia orci. Cras congue est ante, semper dapibus nibh laoreet et. Duis et scelerisque eros. Sed in nisl sed nisi facilisis fringilla feugiat sed est. Sed quis tempus diam. Ut bibendum quam vel mi ultrices hendrerit. Donec fermentum rhoncus tellus. Fusce quis tellus a metus suscipit blandit non eget velit. Curabitur pharetra porttitor ipsum, eu elementum neque elementum ac. Nam accumsan augue lacus, ac tincidunt justo pretium porta. Integer rutrum enim feugiat purus venenatis, quis rutrum nulla tincidunt. Duis faucibus velit id lacus malesuada, nec bibendum elit interdum. Pellentesque id sem a sem tristique fringilla eget ut nibh. Pellentesque ultrices finibus nisl, non egestas quam semper mollis. Nullam ut libero velit.
    Sed ultrices velit eu laoreet pharetra. Nulla nec ex sed elit sodales elementum. Nam rutrum ultrices ante vestibulum consequat. Pellentesque nibh quam, venenatis quis pharetra vitae, congue id enim. Vestibulum tellus nisl, aliquam id pellentesque suscipit, convallis sed ipsum. Quisque semper ut dui non maximus. Fusce eleifend neque vitae orci vulputate, eu viverra mi pellentesque. Suspendisse consequat purus in enim blandit congue. Duis imperdiet consectetur sapien a finibus. Duis aliquam pharetra rutrum. Phasellus mollis sit amet lorem sed vestibulum.
    Phasellus pharetra lacus imperdiet ultricies imperdiet. Ut at dui urna. Aliquam vitae venenatis enim. In hac habitasse platea dictumst. Cras id sapien dui. Aliquam ut velit condimentum, imperdiet ex et, pharetra lacus. Donec ullamcorper risus ac nunc lobortis, sed finibus nisi iaculis. Maecenas sodales at tellus eget varius. Quisque neque arcu, auctor et consectetur sed, consectetur in enim.
    Donec metus nunc, faucibus ac consectetur ut, viverra at lacus. Nunc mattis placerat elit, sit amet lobortis ipsum posuere a. Morbi vel interdum erat, sit amet efficitur nisi. Praesent ut massa ullamcorper, cursus sem quis, luctus orci. Pellentesque fermentum lobortis suscipit. Donec elementum mauris placerat sem porta, ac rhoncus quam lacinia. Maecenas ut augue id elit placerat tincidunt. Mauris aliquet purus massa, vitae accumsan lacus volutpat eu. Cras dignissim tempor purus vel ultricies. Vivamus imperdiet vitae felis nec dictum. Vestibulum posuere tortor sapien, eu elementum magna aliquam sit amet. Nam varius sed odio sed cursus. Curabitur non finibus lacus. Nullam eleifend faucibus tortor vitae cursus. Maecenas ornare lectus eu commodo venenatis.
"""
    .replace('\n', ' ')
    .filter { it.isLetter() || it.isWhitespace() }
    .lowercase()
    .split(' ')
    .filter { it.isNotEmpty() }

private fun fakeContent(words: Int = 200) = (0 until words)
    .map { FakeWords.random() }
    .joinToString(" ") + "."

private fun fakeTime(): Long {
    @OptIn(UnsafeNumber::class)
    val nowMs = memScoped {
        val timeval = alloc<timeval>()
        gettimeofday(timeval.ptr, null)
        timeval.tv_sec * 1000L
    }
    val lastYearMs = nowMs - (1000L * 60 * 60 * 24 * 365)
    return lastYearMs + (Random.nextFloat() * (nowMs - lastYearMs)).toLong()
}

val FakeNotes = (0 until 10).map {
    Note(
        id = Random.nextLong().toString(),
        author = FakeAuthors.random(),
        date = fakeTime(),
        content = fakeContent()
    )
}