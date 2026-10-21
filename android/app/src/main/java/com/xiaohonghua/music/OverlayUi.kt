@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class,androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.xiaohonghua.music

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.findViewTreeOnBackPressedDispatcherOwner
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider

/** Use the page's neutral paper, rather than Material's accent-tinted container roles. */
@Composable internal fun AppDropdownMenu(expanded: Boolean,onDismissRequest:()->Unit,modifier: Modifier=Modifier,
    content: @Composable ColumnScope.()->Unit) {
    DropdownMenu(expanded,onDismissRequest,modifier.widthIn(min=200.dp,max=280.dp),
        shape=RoundedCornerShape(20.dp),containerColor=pageColor,tonalElevation=0.dp,shadowElevation=4.dp,
        border=BorderStroke(.6.dp,MaterialTheme.colorScheme.outlineVariant.copy(alpha=.65f)),content=content)
}

@Composable internal fun AppDropdownMenuItem(text: @Composable ()->Unit,onClick:()->Unit,modifier: Modifier=Modifier,
    enabled: Boolean=true,leadingIcon: (@Composable ()->Unit)?=null) {
    DropdownMenuItem(text={ProvideTextStyle(MaterialTheme.typography.bodyMedium,content=text)},onClick=onClick,
        modifier=modifier.heightIn(min=48.dp),enabled=enabled,leadingIcon=leadingIcon,
        contentPadding=PaddingValues(horizontal=16.dp),colors=MenuDefaults.itemColors(
            textColor=ink,leadingIconColor=MaterialTheme.colorScheme.primary,
            disabledTextColor=muted.copy(alpha=.5f),disabledLeadingIconColor=muted.copy(alpha=.4f)))
}

/** Keep native drag, insets and hide motion; only replace predictive back's whole-sheet scale. */
@Composable internal fun AppModalBottomSheet(onDismissRequest:()->Unit,modifier: Modifier=Modifier,
    sheetState: SheetState=rememberModalBottomSheetState(),sheetGesturesEnabled: Boolean=true,
    dragHandle: (@Composable ()->Unit)?={BottomSheetDefaults.DragHandle(width=32.dp,height=4.dp,color=muted.copy(alpha=.35f))},
    content: @Composable ColumnScope.()->Unit) {
    val dismiss=rememberSheetDismiss(sheetState)
    val latestDismiss by rememberUpdatedState(onDismissRequest)
    var backClosing by remember(sheetState) {mutableStateOf(false)}
    ModalBottomSheet(onDismissRequest={latestDismiss()},modifier=modifier.testTag("app-sheet-surface"),
        sheetState=sheetState,sheetGesturesEnabled=sheetGesturesEnabled && !backClosing,
        shape=RoundedCornerShape(topStart=28.dp,topEnd=28.dp),containerColor=pageColor,contentColor=ink,
        tonalElevation=0.dp,dragHandle=dragHandle,
        properties=ModalBottomSheetProperties(shouldDismissOnBackPress=false)) {
        val view=LocalView.current
        // A sheet has its own ComponentDialog. Never route its back gesture to the activity.
        val owner=remember(view) {checkNotNull(view.findViewTreeOnBackPressedDispatcherOwner())}
        CompositionLocalProvider(LocalOnBackPressedDispatcherOwner provides owner) {
            BackHandler {
                if(!backClosing) {
                    backClosing=true
                    dismiss {latestDismiss()}
                }
            }
            // Nested steps register after the base handler, so back returns to their parent first.
            content()
            val keyboard=LocalSoftwareKeyboardController.current
            val imeVisible=WindowInsets.isImeVisible
            BackHandler(imeVisible) {keyboard?.hide()}
        }
    }
}

/** Window animations survive a caller removing a dialog and preserve rejected form submissions. */
@Composable internal fun AppDialogWindowAnimation() {
    val view=LocalView.current
    SideEffect {
        (view.parent as? DialogWindowProvider)?.window?.let {
            it.setWindowAnimations(R.style.AppOverlayDialogAnimation)
            it.setDimAmount(.24f)
        }
    }
}

@Composable internal fun AppAlertDialog(onDismissRequest:()->Unit,confirmButton: @Composable ()->Unit,
    title: @Composable ()->Unit,text: @Composable ()->Unit,modifier: Modifier=Modifier,
    dismissButton: (@Composable ()->Unit)?=null) {
    AlertDialog(onDismissRequest=onDismissRequest,confirmButton=confirmButton,dismissButton=dismissButton,
        title={AppDialogWindowAnimation();title()},text=text,modifier=modifier.testTag("app-dialog-surface"),
        shape=RoundedCornerShape(24.dp),containerColor=pageColor,tonalElevation=0.dp,
        titleContentColor=ink,textContentColor=muted)
}
