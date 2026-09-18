<template>
  <main class="knowledge-shell" aria-labelledby="knowledge-title">
    <section v-if="!user" class="knowledge-gate">
      <p class="eyebrow">PERSONAL VIDEO INDEX</p>
      <h1 id="knowledge-title">登录后管理你的<br><span lang="en">Video Knowledge</span></h1>
      <p>空间、目录与视频来源由当前账号隔离。上传的视频会自动进入“未分类”，随后可在这里组织。</p>
      <button type="button" class="lime-button" @click="$emit('request-login')">登录以打开知识库</button>
    </section>

    <template v-else>
      <header class="knowledge-heading">
        <div>
          <p class="eyebrow">PERSONAL VIDEO INDEX</p>
          <h1 id="knowledge-title">知识库资产层</h1>
          <p>组织视频来源，为后续跨视频检索、证据回填和 MCP 接入建立稳定边界。</p>
        </div>
        <div class="knowledge-summary" aria-label="当前空间摘要">
          <span><b>{{ spaces.length }}</b> spaces</span>
          <span><b>{{ collections.length }}</b> folders</span>
          <span><b>{{ sourceCount }}</b> sources</span>
        </div>
      </header>

      <p v-if="error" class="knowledge-banner is-error" role="alert">{{ error }}</p>
      <p v-else-if="notice" class="knowledge-banner" role="status">{{ notice }}</p>

      <section class="knowledge-workbench" :aria-busy="loading">
        <aside class="knowledge-rail">
          <div class="rail-heading">
            <span>知识空间</span>
            <button type="button" class="icon-action" aria-label="新建知识空间" @click="toggleSpaceComposer">+</button>
          </div>

          <form v-if="spaceComposerOpen" class="rail-form" @submit.prevent="createSpace">
            <label>
              <span>空间名称</span>
              <input v-model="newSpaceName" maxlength="100" placeholder="例如：学习" autofocus />
            </label>
            <label>
              <span>一句描述，可选</span>
              <input v-model="newSpaceDescription" maxlength="500" placeholder="Java、分布式与面试复习" />
            </label>
            <div class="form-actions">
              <button type="button" @click="closeSpaceComposer">取消</button>
              <button class="text-action" :disabled="saving || !newSpaceName.trim()">创建</button>
            </div>
          </form>

          <nav class="space-list" aria-label="知识空间列表">
            <button
              v-for="space in spaces"
              :key="space.id"
              type="button"
              class="space-item"
              :class="{ active: space.id === selectedSpaceId }"
              @click="selectSpace(space.id)"
            >
              <span class="space-mark">{{ space.systemDefault ? '·' : '#' }}</span>
              <span class="space-copy"><strong>{{ space.name }}</strong><small>{{ space.systemDefault ? '默认接入区' : (space.description || '自定义空间') }}</small></span>
            </button>
          </nav>

          <div class="rail-divider"></div>
          <div class="rail-heading folder-heading">
            <span>目录</span>
            <button type="button" class="icon-action" :disabled="!selectedSpaceId" aria-label="新建目录" @click="openCollectionComposer(selectedCollectionId)">+</button>
          </div>

          <form v-if="collectionComposerOpen" class="rail-form compact-form" @submit.prevent="createCollection">
            <label>
              <span>{{ collectionParent ? `新建于 ${collectionParent.name}` : '新建根目录' }}</span>
              <input v-model="newCollectionName" maxlength="100" placeholder="目录名称" autofocus />
            </label>
            <div class="form-actions">
              <button type="button" @click="closeCollectionComposer">取消</button>
              <button class="text-action" :disabled="saving || !newCollectionName.trim()">创建</button>
            </div>
          </form>

          <div class="folder-list">
            <button type="button" class="folder-item root-item" :class="{ active: selectedCollectionId === null }" @click="selectCollection(null)">
              <span>⌂</span><strong>空间根目录</strong>
            </button>
            <button
              v-for="collection in collections"
              :key="collection.id"
              type="button"
              class="folder-item"
              :class="{ active: collection.id === selectedCollectionId }"
              :style="{ '--depth': collectionDepth(collection) }"
              :title="collection.path"
              @click="selectCollection(collection.id)"
            >
              <span>⌁</span><strong>{{ collection.name }}</strong>
              <small>{{ collection.path }}</small>
            </button>
          </div>
        </aside>

        <section class="source-pane">
          <header class="source-header">
            <div>
              <p class="source-path">{{ selectedSpace?.name || '正在载入' }} <span>/</span> {{ selectedCollection?.name || '根目录' }}</p>
              <h2>{{ selectedCollection?.name || selectedSpace?.name || '知识来源' }}</h2>
            </div>
            <button type="button" class="subtle-button" :disabled="loading" @click="refreshCurrent">刷新</button>
          </header>

          <div v-if="loading" class="knowledge-loading" role="status">正在读取知识资产...</div>
          <div v-else-if="sources.length === 0" class="source-empty">
            <p class="empty-index">000</p>
            <h3>这里还没有内容源</h3>
            <p>从视频工作台上传视频后，它会自动进入“未分类”。你也可以把已入库视频移动到当前目录。</p>
          </div>
          <ul v-else class="source-list">
            <li v-for="source in sources" :key="source.id" class="source-row">
              <div class="source-type">{{ source.sourceType === 'VIDEO' ? 'VID' : source.sourceType }}</div>
              <div class="source-copy">
                <h3 :title="source.title">{{ source.title }}</h3>
                <p><span :class="['status-chip', `status-${source.status.toLowerCase()}`]">{{ source.status }}</span><span>版本 {{ source.currentVersion }}</span><span>{{ formatDate(source.updatedAt) }}</span></p>
              </div>
              <button type="button" class="source-move" @click="openMove(source)">移动</button>
            </li>
          </ul>

          <form v-if="movingSource" class="move-tray" @submit.prevent="moveSource">
            <div>
              <p>移动来源</p>
              <strong>{{ movingSource.title }}</strong>
            </div>
            <label>目标空间
              <select v-model="moveSpaceId" @change="loadMoveCollections">
                <option v-for="space in spaces" :key="space.id" :value="space.id">{{ space.name }}</option>
              </select>
            </label>
            <label>目标目录
              <select v-model="moveCollectionId">
                <option :value="null">空间根目录</option>
                <option v-for="collection in moveCollections" :key="collection.id" :value="collection.id">{{ collection.path }}</option>
              </select>
            </label>
            <div class="move-actions">
              <button type="button" @click="closeMove">取消</button>
              <button class="lime-button" :disabled="saving">确认移动</button>
            </div>
          </form>
        </section>
      </section>
    </template>
  </main>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { apiRequest } from './api'

const props = defineProps({ user: { type: Object, default: null } })
defineEmits(['request-login'])

const spaces = ref([])
const collections = ref([])
const sources = ref([])
const selectedSpaceId = ref(null)
const selectedCollectionId = ref(null)
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const notice = ref('')
const spaceComposerOpen = ref(false)
const newSpaceName = ref('')
const newSpaceDescription = ref('')
const collectionComposerOpen = ref(false)
const collectionParentId = ref(null)
const newCollectionName = ref('')
const movingSource = ref(null)
const moveSpaceId = ref(null)
const moveCollectionId = ref(null)
const moveCollections = ref([])

const selectedSpace = computed(() => spaces.value.find(space => space.id === selectedSpaceId.value) || null)
const selectedCollection = computed(() => collections.value.find(collection => collection.id === selectedCollectionId.value) || null)
const collectionParent = computed(() => collections.value.find(collection => collection.id === collectionParentId.value) || null)
const sourceCount = computed(() => sources.value.length)

watch(() => props.user?.id, async userId => {
  resetState()
  if (userId) await loadSpaces()
}, { immediate: true })

function resetState() {
  spaces.value = []
  collections.value = []
  sources.value = []
  selectedSpaceId.value = null
  selectedCollectionId.value = null
  error.value = ''
  notice.value = ''
  closeSpaceComposer()
  closeCollectionComposer()
  closeMove()
}

async function request(path, options) {
  const response = await apiRequest(path, options)
  if (!response.ok) throw new Error((await response.text()) || '请求未完成')
  return response.json()
}

async function loadSpaces() {
  loading.value = true
  error.value = ''
  try {
    spaces.value = await request('/knowledge/spaces')
    const nextId = spaces.value.some(space => space.id === selectedSpaceId.value)
      ? selectedSpaceId.value
      : spaces.value[0]?.id ?? null
    selectedSpaceId.value = nextId
    await loadCurrentSpace()
  } catch (cause) {
    error.value = cause.message || '无法读取知识空间'
  } finally {
    loading.value = false
  }
}

async function loadCurrentSpace() {
  if (!selectedSpaceId.value) {
    collections.value = []
    sources.value = []
    return
  }
  const spaceId = selectedSpaceId.value
  const collectionId = selectedCollectionId.value
  const sourcePath = new URLSearchParams({ spaceId: String(spaceId) })
  if (collectionId !== null) sourcePath.set('collectionId', String(collectionId))
  const [loadedCollections, loadedSources] = await Promise.all([
    request(`/knowledge/spaces/${spaceId}/collections`),
    request(`/knowledge/sources?${sourcePath}`)
  ])
  if (spaceId !== selectedSpaceId.value || collectionId !== selectedCollectionId.value) return
  collections.value = loadedCollections
  sources.value = loadedSources
}

async function selectSpace(spaceId) {
  if (spaceId === selectedSpaceId.value) return
  selectedSpaceId.value = spaceId
  selectedCollectionId.value = null
  closeCollectionComposer()
  await refreshCurrent()
}

async function selectCollection(collectionId) {
  if (collectionId === selectedCollectionId.value) return
  selectedCollectionId.value = collectionId
  closeCollectionComposer()
  await refreshCurrent()
}

async function refreshCurrent() {
  if (!selectedSpaceId.value) return
  loading.value = true
  error.value = ''
  try {
    await loadCurrentSpace()
  } catch (cause) {
    error.value = cause.message || '无法读取当前目录'
  } finally {
    loading.value = false
  }
}

function toggleSpaceComposer() {
  spaceComposerOpen.value ? closeSpaceComposer() : (spaceComposerOpen.value = true)
}

function closeSpaceComposer() {
  spaceComposerOpen.value = false
  newSpaceName.value = ''
  newSpaceDescription.value = ''
}

async function createSpace() {
  if (!newSpaceName.value.trim()) return
  saving.value = true
  error.value = ''
  try {
    const created = await request('/knowledge/spaces', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name: newSpaceName.value.trim(), description: newSpaceDescription.value.trim() })
    })
    closeSpaceComposer()
    notice.value = `已创建知识空间“${created.name}”`
    await loadSpaces()
    await selectSpace(created.id)
  } catch (cause) {
    error.value = cause.message || '创建知识空间失败'
  } finally {
    saving.value = false
  }
}

function openCollectionComposer(parentId) {
  collectionParentId.value = parentId
  collectionComposerOpen.value = true
}

function closeCollectionComposer() {
  collectionComposerOpen.value = false
  collectionParentId.value = null
  newCollectionName.value = ''
}

async function createCollection() {
  if (!selectedSpaceId.value || !newCollectionName.value.trim()) return
  saving.value = true
  error.value = ''
  try {
    const created = await request(`/knowledge/spaces/${selectedSpaceId.value}/collections`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ parentId: collectionParentId.value, name: newCollectionName.value.trim() })
    })
    closeCollectionComposer()
    notice.value = `已创建目录“${created.name}”`
    await refreshCurrent()
  } catch (cause) {
    error.value = cause.message || '创建目录失败'
  } finally {
    saving.value = false
  }
}

function openMove(source) {
  movingSource.value = source
  moveSpaceId.value = source.spaceId
  moveCollectionId.value = source.collectionId
  loadMoveCollections()
}

function closeMove() {
  movingSource.value = null
  moveSpaceId.value = null
  moveCollectionId.value = null
  moveCollections.value = []
}

async function loadMoveCollections() {
  if (!moveSpaceId.value) return
  try {
    moveCollections.value = await request(`/knowledge/spaces/${moveSpaceId.value}/collections`)
    if (!moveCollections.value.some(collection => collection.id === moveCollectionId.value)) moveCollectionId.value = null
  } catch (cause) {
    error.value = cause.message || '无法读取目标目录'
  }
}

async function moveSource() {
  if (!movingSource.value || !moveSpaceId.value) return
  saving.value = true
  error.value = ''
  try {
    const moved = await request(`/knowledge/sources/${movingSource.value.id}/location`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ spaceId: moveSpaceId.value, collectionId: moveCollectionId.value })
    })
    notice.value = `已移动“${moved.title}”`
    closeMove()
    await refreshCurrent()
  } catch (cause) {
    error.value = cause.message || '移动内容源失败'
  } finally {
    saving.value = false
  }
}

function collectionDepth(collection) {
  return Math.max(0, (collection.path || '').split('/').filter(Boolean).length - 1)
}

function formatDate(value) {
  if (!value) return '等待索引'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? '等待索引' : date.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' })
}
</script>

<style scoped>
.knowledge-shell { width: min(1320px, calc(100% - 48px)); margin: 0 auto; padding: 64px 0 84px; color: var(--text-main); }
.eyebrow { color: var(--accent-lime); font: 700 0.7rem/1.2 monospace; letter-spacing: .14em; }
.knowledge-heading { display: flex; align-items: flex-end; justify-content: space-between; gap: 32px; padding-bottom: 30px; border-bottom: 1px solid var(--border-tech); }
.knowledge-heading h1, .knowledge-gate h1 { margin: 9px 0 8px; font-family: 'Dela Gothic One', 'Noto Sans SC', sans-serif; font-size: clamp(1.7rem, 4vw, 3rem); line-height: 1.25; letter-spacing: -.025em; }
.knowledge-heading > div > p:last-child, .knowledge-gate > p { max-width: 610px; color: var(--text-sub); font-size: .92rem; line-height: 1.8; }
.knowledge-summary { display: flex; gap: 22px; padding-bottom: 4px; color: var(--text-sub); font: .72rem/1.4 monospace; text-transform: uppercase; }
.knowledge-summary span { display: grid; gap: 3px; }
.knowledge-summary b { color: var(--text-main); font-size: 1.2rem; }
.knowledge-workbench { display: grid; grid-template-columns: 278px minmax(0, 1fr); min-height: 520px; border: 1px solid var(--border-tech); background: rgba(18,20,24,.64); }
.knowledge-rail { padding: 22px 14px; background: rgba(5,6,8,.35); border-right: 1px solid var(--border-tech); }
.rail-heading { display: flex; align-items: center; justify-content: space-between; padding: 0 7px 12px; color: var(--text-sub); font: .72rem/1.2 monospace; letter-spacing: .1em; text-transform: uppercase; }
.icon-action, .source-move, .subtle-button, .form-actions button, .move-actions button { min-height: 32px; border: 1px solid transparent; background: transparent; color: var(--text-sub); cursor: pointer; }
.icon-action { width: 32px; color: var(--accent-lime); font-size: 1.25rem; line-height: 1; }
.icon-action:hover:not(:disabled), .subtle-button:hover:not(:disabled), .source-move:hover { border-color: var(--accent-lime); color: var(--accent-lime); }
.icon-action:active, .subtle-button:active, .source-move:active, .lime-button:active, .text-action:active { transform: scale(.96); }
.icon-action:disabled, .subtle-button:disabled { cursor: not-allowed; opacity: .45; }
.space-list, .folder-list { display: grid; gap: 3px; }
.space-item, .folder-item { width: 100%; border: 0; background: transparent; color: var(--text-sub); text-align: left; cursor: pointer; }
.space-item { display: grid; grid-template-columns: 18px minmax(0, 1fr); align-items: center; gap: 8px; min-height: 52px; padding: 7px; }
.space-item:hover, .folder-item:hover { background: rgba(255,255,255,.03); color: var(--text-main); }
.space-item.active, .folder-item.active { color: var(--text-main); background: rgba(197,249,70,.075); }
.space-item.active { box-shadow: inset 2px 0 var(--accent-lime); }
.space-mark { color: var(--accent-lime); font: 700 1rem/1 monospace; }
.space-copy { display: grid; min-width: 0; gap: 3px; }
.space-copy strong, .folder-item strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: .86rem; font-weight: 700; }
.space-copy small, .folder-item small { overflow: hidden; color: var(--text-sub); text-overflow: ellipsis; white-space: nowrap; font: .66rem/1.2 monospace; }
.rail-divider { height: 1px; margin: 20px 7px; background: var(--border-tech); }
.folder-heading { padding-bottom: 8px; }
.folder-item { position: relative; display: grid; grid-template-columns: 15px minmax(0, 1fr); align-items: center; column-gap: 6px; min-height: 31px; padding: 5px 7px 5px calc(7px + var(--depth, 0) * 14px); }
.folder-item span { color: var(--accent-lime); font: .75rem/1 monospace; }
.folder-item small { grid-column: 2; display: none; }
.root-item { margin-bottom: 4px; }
.rail-form { display: grid; gap: 9px; margin: 0 3px 12px; padding: 11px; border: 1px solid rgba(197,249,70,.34); background: rgba(197,249,70,.035); }
.rail-form label { display: grid; gap: 5px; color: var(--text-sub); font: .65rem/1.2 monospace; }
.rail-form input, .move-tray select { width: 100%; border: 1px solid var(--border-tech); border-radius: 0; background: #090a0d; color: var(--text-main); padding: 8px; outline: none; }
.rail-form input:focus, .move-tray select:focus { border-color: var(--accent-lime); }
.form-actions, .move-actions { display: flex; justify-content: flex-end; gap: 8px; }
.form-actions button, .move-actions button { padding: 5px 8px; font-size: .72rem; }
.text-action { color: var(--accent-lime) !important; }
.source-pane { position: relative; min-width: 0; padding: 30px 34px; }
.source-header { display: flex; justify-content: space-between; align-items: flex-start; gap: 20px; padding-bottom: 22px; border-bottom: 1px solid var(--border-tech); }
.source-path { color: var(--text-sub); font: .72rem/1.5 monospace; }
.source-path span { color: var(--accent-lime); margin: 0 5px; }
.source-header h2 { margin-top: 5px; font-size: 1.5rem; line-height: 1.25; }
.subtle-button { padding: 6px 11px; font: .72rem/1 monospace; }
.knowledge-loading { min-height: 250px; display: grid; place-items: center; color: var(--text-sub); font: .82rem monospace; }
.source-empty { min-height: 300px; display: grid; align-content: center; justify-items: start; max-width: 490px; }
.empty-index { color: var(--accent-lime); font: 700 2.5rem/.9 'Syncopate', monospace; opacity: .8; }
.source-empty h3 { margin: 15px 0 7px; font-size: 1rem; }
.source-empty p:last-child { color: var(--text-sub); font-size: .86rem; line-height: 1.8; }
.source-list { list-style: none; margin: 0; padding: 4px 0 96px; }
.source-row { display: grid; grid-template-columns: 40px minmax(0, 1fr) auto; align-items: center; gap: 14px; padding: 17px 0; border-bottom: 1px solid rgba(42,45,53,.8); }
.source-type { display: grid; place-items: center; width: 36px; height: 36px; background: rgba(197,249,70,.09); color: var(--accent-lime); font: 700 .66rem/1 monospace; letter-spacing: .05em; }
.source-copy { min-width: 0; }
.source-copy h3 { overflow: hidden; margin: 0 0 7px; font-size: .92rem; line-height: 1.3; text-overflow: ellipsis; white-space: nowrap; }
.source-copy p { display: flex; flex-wrap: wrap; gap: 7px 12px; color: var(--text-sub); font: .67rem/1.2 monospace; }
.status-chip { color: #e0e0e0; }
.status-pending { color: #f2bf6b; }
.status-indexed { color: var(--accent-lime); }
.source-move { padding: 6px 10px; font: .7rem/1 monospace; }
.move-tray { position: sticky; bottom: 0; display: grid; grid-template-columns: minmax(180px, 1fr) minmax(130px, .7fr) minmax(150px, .8fr) auto; align-items: end; gap: 13px; margin: 0 -34px -30px; padding: 16px 34px; border-top: 1px solid rgba(197,249,70,.5); background: #111318; box-shadow: 0 -16px 30px rgba(0,0,0,.25); }
.move-tray p, .move-tray label { display: block; margin-bottom: 4px; color: var(--text-sub); font: .65rem/1.2 monospace; }
.move-tray strong { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: .78rem; }
.lime-button { min-height: 38px; border: 0; border-radius: 0; background: var(--accent-lime); color: var(--text-inverse); padding: 0 15px; font-weight: 800; cursor: pointer; }
.move-actions { align-items: end; }
.knowledge-banner { margin: 16px 0; padding: 10px 13px; border-left: 2px solid var(--accent-lime); background: rgba(197,249,70,.055); color: var(--text-main); font: .76rem/1.5 monospace; }
.knowledge-banner.is-error { border-color: #ff6876; background: rgba(255,104,118,.09); color: #ffadb5; }
.knowledge-gate { display: grid; min-height: 58vh; align-content: center; justify-items: start; max-width: 670px; }
.knowledge-gate h1 span { color: var(--accent-lime); }
.knowledge-gate .lime-button { margin-top: 25px; }
@media (max-width: 760px) {
  .knowledge-shell { width: min(100% - 28px, 1320px); padding: 36px 0 50px; }
  .knowledge-heading { display: block; padding-bottom: 22px; }
  .knowledge-summary { margin-top: 22px; }
  .knowledge-workbench { display: block; }
  .knowledge-rail { border-right: 0; border-bottom: 1px solid var(--border-tech); }
  .space-list { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .source-pane { padding: 24px 18px; }
  .move-tray { position: static; grid-template-columns: 1fr; margin: 18px -18px -24px; padding: 16px 18px; }
  .move-actions { justify-content: flex-end; }
  .source-row { grid-template-columns: 36px minmax(0, 1fr); }
  .source-move { grid-column: 2; justify-self: start; margin-top: -6px; }
}
@media (prefers-reduced-motion: reduce) { .icon-action:active, .subtle-button:active, .source-move:active, .lime-button:active, .text-action:active { transform: none; } }
</style>
