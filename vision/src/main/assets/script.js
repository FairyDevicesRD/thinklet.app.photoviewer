const galleryContainer = document.getElementById('gallery-container');
const imageModal = document.getElementById('imageModal');
const modalImage = document.getElementById('modalImage');
const liveImage = document.getElementById('live-image');
const deleteSelectedImagesBtn = document.getElementById('deleteSelectedImagesBtn');

const selectAllBtn = document.getElementById('selectAllBtn');
const deselectAllBtn = document.getElementById('deselectAllBtn');
const downloadSelectedBtn = document.getElementById('downloadSelectedBtn');

let liveViewIntervalId = null;
const liveViewUpdateInterval = 1000;

function startLiveView() {
    if (liveViewIntervalId) {
        clearInterval(liveViewIntervalId);
    }
    liveViewIntervalId = setInterval(() => {
        liveImage.src = "/image?" + new Date().getTime();
    }, liveViewUpdateInterval);
}

function stopLiveView() {
    if (liveViewIntervalId) {
        clearInterval(liveViewIntervalId);
        liveViewIntervalId = null;
    }
}

function sendCameraAction(endpoint) {
    fetch(endpoint)
        .then(response => {
            if (!response.ok) throw new Error('Camera action failed: ' + response.statusText);
            return response.text();
        })
        .then(text => {
            console.log("Camera action response:", text);
            if (endpoint === '/capture') {
                setTimeout(loadImages, 1500);
            }
        })
        .catch(error => console.error("Error sending camera action:", error));
}

async function loadImages() {
    try {
        const response = await fetch('/images_list');
        if (!response.ok) throw new Error('Failed to load images list: ' + response.statusText);
        const images = await response.json();

        galleryContainer.innerHTML = '';

        const hasImages = images.length > 0;
        if (deleteSelectedImagesBtn) {
            deleteSelectedImagesBtn.style.display = hasImages ? 'inline-block' : 'none';
        }
        if (downloadSelectedBtn) {
            downloadSelectedBtn.style.display = hasImages ? 'inline-block' : 'none';
        }
        if (selectAllBtn) {
            selectAllBtn.style.display = hasImages ? 'inline-block' : 'none';
        }
        if (deselectAllBtn) {
            deselectAllBtn.style.display = hasImages ? 'inline-block' : 'none';
        }


        if (!hasImages) {
            galleryContainer.innerHTML = '<p style="text-align:center; color:#777;">撮影された画像はありません。</p>';
            return;
        }

        images.forEach(imageInfo => {
            const itemDiv = document.createElement('div');
            itemDiv.className = 'gallery-item';

            const checkbox = document.createElement('input');
            checkbox.type = 'checkbox';
            checkbox.className = 'image-checkbox';
            checkbox.value = imageInfo.filename;
            itemDiv.appendChild(checkbox);

            const img = document.createElement('img');
            img.src = "/image_file/" + encodeURIComponent(imageInfo.filename);
            img.alt = "Captured image " + imageInfo.filename;
            img.loading = 'lazy';
            img.onclick = () => openModal(img.src);

            const infoP = document.createElement('p');
            infoP.className = 'info';
            infoP.textContent = imageInfo.formattedDate;

            itemDiv.appendChild(img);
            itemDiv.appendChild(infoP);
            galleryContainer.appendChild(itemDiv);
        });
    } catch (error) {
        console.error("Error loading images:", error);
        galleryContainer.innerHTML = '<p style="text-align:center; color:red;">画像一覧の読み込みに失敗しました。</p>';
        if (deleteSelectedImagesBtn) {
            deleteSelectedImagesBtn.style.display = 'none';
        }
        if (downloadSelectedBtn) {
            downloadSelectedBtn.style.display = 'none';
        }
        if (selectAllBtn) {
            selectAllBtn.style.display = 'none';
        }
        if (deselectAllBtn) {
            deselectAllBtn.style.display = 'none';
        }
    }
}

function openModal(src) {
    modalImage.src = src;
    imageModal.style.display = 'flex';
}

function closeModal() {
    imageModal.style.display = 'none';
}

window.onclick = function(event) {
    if (event.target == imageModal) {
        closeModal();
    }
}

function getSelectedFilenames() {
    const filenames = [];
    const checkboxes = galleryContainer.querySelectorAll('.image-checkbox:checked');
    checkboxes.forEach(cb => {
        filenames.push(cb.value);
    });
    return filenames;
}


if (deleteSelectedImagesBtn) {
    deleteSelectedImagesBtn.onclick = async () => {
        const filenamesToDelete = getSelectedFilenames();

        if (filenamesToDelete.length === 0) {
            alert("削除する画像を選択してください。");
            return;
        }

        if (!confirm(`選択された ${filenamesToDelete.length} 個の画像を本当に削除しますか？この操作は元に戻せません。`)) {
            return;
        }

        console.log("Attempting to delete selected files:", filenamesToDelete);

        try {
            const response = await fetch('/delete_selected_images', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({ filenames: filenamesToDelete })
            });

            const result = await response.json();

            if (response.ok) {
                alert(result.message || "選択された画像の削除処理が完了しました。");
                console.log("Batch delete successful:", result);
                if (result.details && result.details.length > 0) {
                    console.log("Deletion details:", result.details.join("\n"));
                }
            } else {
                alert(`画像の削除中にエラーが発生しました: ${result.message || response.statusText}`);
                console.error("Batch delete failed:", response.status, result);
            }

        } catch (error) {
            alert(`削除処理中に予期せぬエラーが発生しました: ${error.message}`);
            console.error("Error during batch delete fetch:", error);
        }
        loadImages();
    };
}

if (selectAllBtn) {
    selectAllBtn.onclick = () => {
        const checkboxes = galleryContainer.querySelectorAll('.image-checkbox');
        checkboxes.forEach(cb => cb.checked = true);
    };
}

if (deselectAllBtn) {
    deselectAllBtn.onclick = () => {
        const checkboxes = galleryContainer.querySelectorAll('.image-checkbox');
        checkboxes.forEach(cb => cb.checked = false);
    };
}

if (downloadSelectedBtn) {
    downloadSelectedBtn.onclick = async () => {
        const filenamesToDownload = getSelectedFilenames();

        if (filenamesToDownload.length === 0) {
            alert('ダウンロードする画像を選択してください。');
            return;
        }

        console.log('Requesting ZIP download for:', filenamesToDownload);
        try {
            const response = await fetch('/download_selected_zip', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({ filenames: filenamesToDownload }),
            });

            if (response.ok) {
                const blob = await response.blob();
                const url = window.URL.createObjectURL(blob);
                const a = document.createElement('a');
                a.style.display = 'none';
                a.href = url;

                const disposition = response.headers.get('Content-Disposition');
                let downloadFilename = 'selected_images.zip';
                if (disposition && disposition.indexOf('attachment') !== -1) {
                    const filenameRegex = /filename[^;=\n]*=((['"]).*?\2|[^;\n]*)/;
                    const matches = filenameRegex.exec(disposition);
                    if (matches != null && matches[1]) {
                        downloadFilename = matches[1].replace(/['"]/g, '');
                    }
                }
                a.download = downloadFilename;
                document.body.appendChild(a);
                a.click();
                window.URL.revokeObjectURL(url);
                document.body.removeChild(a);
                console.log('ZIP download initiated.');
            } else {
                let errorMsg = `ダウンロードエラー: ${response.statusText}`;
                try {
                    const errorData = await response.json();
                    errorMsg = `ダウンロードエラー: ${errorData.message || response.statusText}`;
                } catch (e) {
                    console.warn("Could not parse error response as JSON for download.");
                }
                alert(errorMsg);
                console.error('Error downloading ZIP:', response.status, errorMsg);
            }
        } catch (error) {
            alert('ダウンロードリクエストの送信に失敗しました。コンソールを確認してください。');
            console.error('Failed to send download request:', error);
        }
    };
}


window.onload = function() {
    loadImages();
    startLiveView();
};

document.addEventListener("visibilitychange", function() {
    if (document.hidden) {
        stopLiveView();
    } else {
        startLiveView();
    }
});
