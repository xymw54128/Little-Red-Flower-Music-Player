@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.xiaohonghua.music

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable internal fun PlaylistEditSheet(playlist: Playlist,onDismiss:()->Unit,onSave:(String,String)->Unit,onCover:()->Unit) {
    var name by rememberSaveable(playlist.id) {mutableStateOf(playlist.name)}
    var description by rememberSaveable(playlist.id) {mutableStateOf(playlist.description)}
    var submitted by remember(playlist.id) {mutableStateOf(false)}
    val focus=LocalFocusManager.current
    val keyboard=LocalSoftwareKeyboardController.current
    val trimmedName=if(playlist.isFavorites) "我喜欢" else name.trim()
    val nameError=when {
        trimmedName.isBlank() -> "请输入歌单名称"
        trimmedName.length > 60 -> "歌单名称最多 60 个字"
        !playlist.isFavorites && trimmedName == "我喜欢" -> "“我喜欢”是默认歌单，请使用其他名称"
        else -> null
    }
    val valid=nameError == null && description.length <= 300
    fun submit() {
        if(valid && !submitted) {
            submitted=true
            keyboard?.hide();focus.clearFocus()
            onSave(trimmedName,description.trim())
        }
    }
    ModalBottomSheet(onDismissRequest=onDismiss,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
        Column(Modifier.fillMaxWidth().heightIn(max=with(LocalDensity.current) {LocalWindowInfo.current.containerSize.height.toDp()}*.88f).imePadding()) {
            SheetTitle("编辑歌单")
            Column(Modifier.weight(1f,fill=false).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=24.dp),
                verticalArrangement=Arrangement.spacedBy(16.dp)) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                    PlaylistCover(playlist,Modifier.size(76.dp))
                    OutlinedButton(onClick={keyboard?.hide();focus.clearFocus();onCover()},enabled=!submitted,
                        modifier=Modifier.testTag("playlist-edit-cover")) {
                        Icon(Icons.Outlined.Edit,null,Modifier.size(18.dp));Spacer(Modifier.width(8.dp));Text("更换封面")
                    }
                }
                OutlinedTextField(name,{if(it.length <= 60) {name=it;submitted=false}},
                    Modifier.testTag("playlist-edit-name").fillMaxWidth(),enabled=!playlist.isFavorites,
                    singleLine=true,label={Text("歌单名称")},isError=nameError != null,
                    supportingText={Text(if(playlist.isFavorites) "默认歌单名称固定" else nameError ?: "${name.length}/60")},
                    shape=RoundedCornerShape(16.dp),keyboardOptions=KeyboardOptions(imeAction=ImeAction.Next),
                    keyboardActions=KeyboardActions(onNext={focus.moveFocus(FocusDirection.Next)}))
                OutlinedTextField(description,{if(it.length <= 300) {description=it;submitted=false}},
                    Modifier.testTag("playlist-edit-description").fillMaxWidth(),label={Text("歌单描述")},
                    placeholder={Text("写下这份歌单的心情（选填）")},minLines=3,maxLines=5,
                    supportingText={Text("${description.length}/300")},shape=RoundedCornerShape(16.dp),
                    keyboardOptions=KeyboardOptions(imeAction=ImeAction.Done),keyboardActions=KeyboardActions(onDone={submit()}))
            }
            Row(Modifier.fillMaxWidth().padding(horizontal=24.dp,vertical=16.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick=onDismiss,modifier=Modifier.weight(1f)) {Text("取消")}
                Button(onClick=::submit,enabled=valid && !submitted,modifier=Modifier.testTag("playlist-edit-save").weight(1f)) {Text("保存")}
            }
        }
    }
}

@Composable internal fun PlaylistSongPicker(title: String,songs: List<Song>,initialSelected: Set<String> = emptySet(),
    excludedIds: Set<String> = emptySet(),onDismiss:()->Unit,onConfirm:(List<String>)->Unit) {
    var query by rememberSaveable(title) {mutableStateOf("")}
    var selectedIds by rememberSaveable(title) {mutableStateOf(initialSelected.toList())}
    var submitted by remember(title) {mutableStateOf(false)}
    val focus=LocalFocusManager.current
    val keyboard=LocalSoftwareKeyboardController.current
    val orderedSongs=remember(songs) {songs.distinctBy {it.id}}
    val eligibleIds=remember(orderedSongs,excludedIds) {orderedSongs.map {it.id}.filterNot(excludedIds::contains).toSet()}
    val selection=remember(selectedIds,eligibleIds) {selectedIds.toSet().intersect(eligibleIds)}
    val searchIndex=rememberSongSearchIndex(orderedSongs)
    val matching=remember(orderedSongs,query,searchIndex) {searchIndex.filter(orderedSongs,query)}
    val matchingIds=remember(matching,eligibleIds) {matching.map {it.id}.filter(eligibleIds::contains).toSet()}
    val allSelected=matchingIds.isNotEmpty() && selection.containsAll(matchingIds)
    // Prune removed songs and new members without resetting the user's remaining choices.
    LaunchedEffect(eligibleIds) {selectedIds=selectedIds.filter(eligibleIds::contains)}
    fun hideKeyboard() {keyboard?.hide();focus.clearFocus()}
    fun submit() {
        if(selection.isNotEmpty() && !submitted) {
            submitted=true
            hideKeyboard()
            onConfirm(orderedSongs.map {it.id}.filter(selection::contains))
        }
    }
    ModalBottomSheet(onDismissRequest=onDismiss,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
        Column(Modifier.fillMaxWidth().heightIn(max=with(LocalDensity.current) {LocalWindowInfo.current.containerSize.height.toDp()}*.88f).imePadding()) {
            SheetTitle(title)
            OutlinedTextField(query,{query=it},Modifier.testTag("playlist-songs-search").fillMaxWidth().padding(horizontal=24.dp),
                singleLine=true,placeholder={Text("搜索歌曲、歌手或专辑",fontSize=13.sp)},
                leadingIcon={Icon(Icons.Outlined.Search,null)},trailingIcon={if(query.isNotEmpty()) {
                    ExpressiveIconButton(onClick={query=""}) {Icon(Icons.Outlined.Close,"清空搜索")}
                }},shape=RoundedCornerShape(16.dp),keyboardOptions=KeyboardOptions(imeAction=ImeAction.Search),
                keyboardActions=KeyboardActions(onSearch={hideKeyboard()}))
            Row(Modifier.fillMaxWidth().padding(start=24.dp,end=16.dp,top=4.dp,bottom=4.dp),verticalAlignment=Alignment.CenterVertically) {
                Text("已选 ${selection.size} 首",Modifier.weight(1f),fontSize=13.sp,color=muted)
                TextButton(onClick={
                    selectedIds=if(allSelected) selectedIds.filterNot(matchingIds::contains) else (selection+matchingIds).toList()
                },enabled=matchingIds.isNotEmpty() && !submitted,modifier=Modifier.testTag("playlist-songs-select-all")) {
                    Text(if(allSelected) "取消全选" else if(query.isBlank()) "全选" else "全选搜索结果")
                }
            }
            HorizontalDivider(color=muted.copy(alpha=.12f))
            if(matching.isEmpty()) Box(Modifier.weight(1f).fillMaxWidth().padding(24.dp),contentAlignment=Alignment.Center) {
                Text(if(query.isBlank()) "这里还没有歌曲" else if(!searchIndex.readyFor(orderedSongs)) "正在准备拼音搜索…" else "没有找到匹配的歌曲",color=muted,fontSize=14.sp)
            } else LazyColumn(Modifier.testTag("playlist-songs-list").weight(1f).fillMaxWidth(),contentPadding=PaddingValues(vertical=4.dp)) {
                items(matching,key={it.id}) {song ->
                    val excluded=song.id in excludedIds
                    val checked=song.id in selection
                    Row(Modifier.testTag("playlist-song-choice-${song.id}").fillMaxWidth().heightIn(min=64.dp)
                        .background(if(checked) MaterialTheme.colorScheme.primary.copy(alpha=.055f) else Color.Transparent)
                        .toggleable(checked,enabled=!excluded && !submitted,role=Role.Checkbox,onValueChange={
                            selectedIds=if(it) selectedIds+song.id else selectedIds.filterNot {id ->id == song.id}
                        }).padding(start=24.dp,end=12.dp,top=8.dp,bottom=8.dp),verticalAlignment=Alignment.CenterVertically) {
                        Cover(song,Modifier.size(44.dp))
                        Column(Modifier.weight(1f).padding(horizontal=12.dp)) {
                            Text(song.title,maxLines=1,overflow=TextOverflow.Ellipsis,fontSize=15.sp,
                                fontWeight=if(checked) FontWeight.SemiBold else FontWeight.Normal,color=if(excluded) muted else ink)
                            Text("${song.artist} · ${song.album}",Modifier.padding(top=4.dp),maxLines=1,overflow=TextOverflow.Ellipsis,fontSize=11.sp,color=muted)
                        }
                        if(excluded) Text("已添加",fontSize=11.sp,color=muted,modifier=Modifier.padding(end=8.dp))
                        Checkbox(checked,onCheckedChange=null,enabled=!excluded && !submitted,modifier=Modifier.size(48.dp))
                    }
                }
            }
            HorizontalDivider(color=muted.copy(alpha=.12f))
            Row(Modifier.fillMaxWidth().padding(horizontal=24.dp,vertical=16.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick=onDismiss,modifier=Modifier.weight(1f)) {Text("取消")}
                Button(onClick=::submit,enabled=selection.isNotEmpty() && !submitted,
                    modifier=Modifier.testTag("playlist-songs-confirm").weight(1f)) {Text("确定${if(selection.isEmpty()) "" else "（${selection.size}）"}")}
            }
        }
    }
}
