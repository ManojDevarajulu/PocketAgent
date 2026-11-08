package dev.pocketagent.device

/** A platform-free copy of one accessibility node, as read from the live tree. */
data class RawNode(
    val className: String?,
    val text: String?,
    val description: String?,
    val clickable: Boolean,
    val editable: Boolean,
    val scrollable: Boolean,
    val isPassword: Boolean,
    val visibleToUser: Boolean,
    val packageName: String,
    val bounds: Bounds,
    val children: List<RawNode>,
)
