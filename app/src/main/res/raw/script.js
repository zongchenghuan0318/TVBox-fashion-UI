// ==========================================================================
// TVBox Web Companion Client Logic
// ==========================================================================

let current_root = '';
let current_parent = '';
let current_remote = '';
let current_file = '';
let hudTimer = null;

function hudToast(message, type) {
    let $hud = $('#hudToast');
    let $msg = $('#hudMessage');
    let $icon = $('#hudIcon');
    if (!$hud.length) return;

    $msg.text(message);
    if (type === 'warn') {
        $icon.html('<circle cx="12" cy="12" r="10"></circle><line x1="12" y1="8" x2="12" y2="12"></line><line x1="12" y1="16" x2="12.01" y2="16"></line>');
        $icon.css('color', '#ff9f0a');
    } else {
        $icon.html('<polyline points="20 6 9 17 4 12"></polyline>');
        $icon.css('color', '#30d158');
    }

    $hud.addClass('show');
    if (hudTimer) clearTimeout(hudTimer);
    hudTimer = setTimeout(function () {
        $hud.removeClass('show');
    }, 2400);
}

function warnToast(msg) {
    hudToast(msg, 'warn');
}

function search() {
    let word = $('#search_key_word').val().trim();
    if (!word) {
        hudToast('请输入搜索关键字', 'warn');
        return;
    }
    doAction('search', { word: word });
    hudToast('已发送搜索「' + word + '」至电视', 'success');
}

function api() {
    let url = $('#diy_api_url').val().trim();
    if (!url) {
        hudToast('请输入自定义配置接口地址', 'warn');
        return;
    }
    doAction('api', { url: url });
    hudToast('接口配置地址已同步至电视', 'success');
}

function push() {
    let url = $('#push_url').val().trim();
    if (!url) {
        hudToast('请输入需要推送播放的地址', 'warn');
        return;
    }
    doAction('push', { url: url });
    hudToast('视频链接已推送到电视播放', 'success');
}

function doAction(action, kv) {
    kv['do'] = action;
    $.post('/action', kv, function (data) {
        console.log(data);
    });
    return false;
}

function tpl_top(path) {
    return `<a class="file-row" href="javascript:void(0)" onclick="listFile('` + path + `')">
    <div class="file-glyph">
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="19" y1="12" x2="5" y2="12"></line><polyline points="12 19 5 12 12 5"></polyline></svg>
    </div>
    <div class="file-meta">
        <div class="file-label">.. (返回上级目录)</div>
        <div class="file-subtext">返回上一层文件夹</div>
    </div>
    <div class="file-chevron">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="9 18 15 12 9 6"></polyline></svg>
    </div>
    </a>`;
}

function tpl_dir(name, time, path) {
    return `<a class="file-row" href="javascript:void(0)" onclick="listFile('` + path + `')">
    <div class="file-glyph">
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"></path></svg>
    </div>
    <div class="file-meta">
        <div class="file-label">` + name + `</div>
        <div class="file-subtext">` + time + `</div>
    </div>
    <div class="file-chevron">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="9 18 15 12 9 6"></polyline></svg>
    </div>
    </a>`;
}

function tpl_file(name, time, path, canDel) {
    return `<a class="file-row" href="javascript:void(0)" onclick="selectFile('` + path + `', ` + canDel + `)">
    <div class="file-glyph">
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M13 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V9z"></path><polyline points="13 2 13 9 20 9"></polyline></svg>
    </div>
    <div class="file-meta">
        <div class="file-label">` + name + `</div>
        <div class="file-subtext">` + time + `</div>
    </div>
    <div class="file-chevron">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="1"></circle><circle cx="19" cy="12" r="1"></circle><circle cx="5" cy="12" r="1"></circle></svg>
    </div>
    </a>`;
}

function clear_list() {
    $('#file_list').html('');
}

function add_file(node) {
    $('#file_list').append(node);
}

function selectFile(path, canDel) {
    current_file = path;
    if (canDel)
        $("#delFileBtn").show();
    else
        $("#delFileBtn").hide();

    $("#fileUrl1").val("clan://localhost/" + current_file);
    $("#fileUrl2").val(current_remote + current_file);
    $("#fileInfoDialog").show();
}

function fileToApi(type) {
    if (type === 1) {
        doAction('api', { url: "clan://localhost/" + current_file });
    } else {
        doAction('api', { url: current_remote + current_file });
    }
    hudToast('已将文件应用为当前接口配置', 'success');
    hideFileInfo();
}

function hideFileInfo() {
    $("#fileInfoDialog").hide();
}

function listFile(path) {
    $('#loadingToast').show();
    $.get('/file/' + path, function (res) {
        let info = JSON.parse(res);
        let parent = info.parent;
        let canDel = info.del === 1;
        current_root = path;
        current_parent = parent;
        current_remote = info.remote;
        let array = info.files;
        if (path === '' && array.length === 0)
            warnToast('读取本地文件失败，可能没有存储权限');
        clear_list();
        if (parent !== '.')
            add_file(tpl_top(parent));

        if (canDel) {
            $('#delCurFolder').show();
        } else {
            $('#delCurFolder').hide();
        }

        array.forEach(node => {
            if (node.dir === 1) {
                add_file(tpl_dir(node.name, node.time, node.path));
            } else {
                add_file(tpl_file(node.name, node.time, node.path, canDel));
            }
        });
        $('#loadingToast').hide();
    }).fail(function () {
        $('#loadingToast').hide();
        warnToast('获取文件列表失败');
    });
}

function uploadFile() {
    $('#file_uploader').click();
}

function uploadTip() {
    let files = $('#file_uploader')[0].files;
    if (files.length <= 0)
        return false;
    let tip = '';
    for (var i = 0; i < files.length; i++) {
        tip += files[i].name + (i < files.length - 1 ? '、' : '');
    }
    $('#uploadTipContent').text(tip);
    $('#uploadTip').show();
}

function doUpload(yes) {
    $('#uploadTip').hide();
    if (yes === 1) {
        let files = $('#file_uploader')[0].files;
        if (files.length <= 0)
            return false;
        var formData = new FormData();
        formData.append('path', current_root);
        for (var i = 0; i < files.length; i++) {
            formData.append("files-" + i, files[i]);
        }
        $('#loadingToast').show();
        $.ajax({
            url: '/upload',
            type: 'post',
            data: formData,
            processData: false,
            contentType: false,
            complete: function () {
                $('#loadingToast').hide();
                hudToast('文件上传完成', 'success');
                listFile(current_root);
            }
        });
    }
}

function newFolder() {
    $('#newFolderContent').val('');
    $('#newFolder').show();
}

function doNewFolder(yes) {
    $('#newFolder').hide();
    if (yes === 1) {
        let name = $('#newFolderContent').val().trim();
        if (name.length <= 0)
            return false;
        $('#loadingToast').show();
        $.post('/newFolder', { path: current_root, name: '' + name }, function () {
            $('#loadingToast').hide();
            hudToast('已新建文件夹「' + name + '」', 'success');
            listFile(current_root);
        }).fail(function () {
            $('#loadingToast').hide();
            warnToast('创建文件夹失败');
        });
    }
}

function delFolder() {
    $('#delFolderContent').text('是否确认删除文件夹「' + current_root + '」？');
    $('#delFolder').show();
}

function doDelFolder(yes) {
    $('#delFolder').hide();
    if (yes === 1) {
        $('#loadingToast').show();
        $.post('/delFolder', { path: current_root }, function () {
            $('#loadingToast').hide();
            hudToast('文件夹已删除', 'success');
            listFile(current_parent);
        }).fail(function () {
            $('#loadingToast').hide();
            warnToast('删除文件夹失败');
        });
    }
}

function delFile() {
    hideFileInfo();
    $('#delFileContent').text('是否确认删除文件「' + current_file + '」？');
    $('#delFile').show();
}

function doDelFile(yes) {
    $('#delFile').hide();
    if (yes === 1) {
        $('#loadingToast').show();
        $.post('/delFile', { path: current_file }, function () {
            $('#loadingToast').hide();
            hudToast('文件已删除', 'success');
            listFile(current_root);
        }).fail(function () {
            $('#loadingToast').hide();
            warnToast('删除文件失败');
        });
    }
}

function showPanel(id) {
    $('.tab-btn').removeClass('active');
    $('#tab' + id).addClass('active');

    $('#panel1, #panel2, #panel3').hide();
    $('#panel' + id).fadeIn(150);

    if (id === 3 && current_remote.length === 0) {
        listFile('');
    }
}

$(function () {
    // 键盘回车快捷响应
    $('#search_key_word').on('keypress', function (e) {
        if (e.which === 13) {
            search();
            return false;
        }
    });
    $('#push_url').on('keypress', function (e) {
        if (e.which === 13) {
            push();
            return false;
        }
    });
    $('#diy_api_url').on('keypress', function (e) {
        if (e.which === 13) {
            api();
            return false;
        }
    });
    $('#newFolderContent').on('keypress', function (e) {
        if (e.which === 13) {
            doNewFolder(1);
            return false;
        }
    });

    var url = window.location.href;
    if (url.indexOf('push.html') > 0)
        showPanel(2);
    else if (url.indexOf('api.html') > 0)
        showPanel(3);
    else if (url.indexOf('all.html') > 0)
        showPanel(3);
    else
        showPanel(1);
});
