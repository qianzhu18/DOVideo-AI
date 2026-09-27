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
          <div class="rail-heading"><span>本地目录导入</span></div>
          <form class="rail-form compact-form" @submit.prevent="runIngest(true)">
            <label>
              <span>本机视频目录（需在授权根目录内）</span>
              <input v-model="ingestPath" placeholder="/path/to/videos" aria-label="导入目录" />
            </label>
            <div class="form-actions">
              <button type="submit" class="text-action" :disabled="ingestBusy || !ingestPath.trim() || !selectedSpaceId">
                {{ ingestBusy ? '处理中…' : '扫描预览' }}
              </button>
              <button type="button" class="text-action" :disabled="ingestBusy || !ingestPath.trim() || !selectedSpaceId || !ingestPlan" @click="applyIngest">
                导入
              </button>
            </div>
            <p v-if="ingestError" class="ingest-error" role="alert">{{ ingestError }}</p>
          </form>
          <div v-if="ingestPlan" class="ingest-plan">
            <p class="ingest-plan-head">
              {{ ingestPlan.dryRun ? '预览' : '已导入' }}：
              新建 {{ ingestPlan.createdCount }} · 变更 {{ ingestPlan.changedCount }} ·
              移动 {{ ingestPlan.movedCount }} · 删除 {{ ingestPlan.deletedCount }} ·
              不变 {{ ingestPlan.unchangedCount }} · 错误 {{ ingestPlan.errorCount }}
            </p>
            <ul>
              <li v-for="(action, index) in ingestActions" :key="index">
                <span :class="['ingest-action', `is-${action.action.toLowerCase()}`]">{{ action.action }}</span>
                <span class="ingest-path">{{ action.path.split('/').pop() }}</span>
                <small v-if="action.error">{{ action.error }}</small>
              </li>
            </ul>
          </div>

          <div class="rail-divider"></div>
          <div class="rail-heading">
            <span>脚本 / 笔记</span>
            <button
              type="button"
              class="icon-action"
              :disabled="!selectedSpaceId"
              aria-label="上传口播稿或笔记"
              @click="scriptComposerOpen = !scriptComposerOpen"
            >+</button>
          </div>
          <form v-if="scriptComposerOpen" class="rail-form" @submit.prevent="createScript">
            <label>
              <span>标题（留空取首行）</span>
              <input v-model="newScriptTitle" maxlength="255" placeholder="例如：缓存击穿口播稿" />
            </label>
            <label>
              <span>粘贴 Markdown 或纯文本，空行分段</span>
              <textarea
                v-model="newScriptContent"
                rows="6"
                maxlength="200000"
                placeholder="口播稿 / 课程笔记；每个段落会成为独立的知识单元并进入跨视频检索"
                aria-label="脚本内容"
              ></textarea>
            </label>
            <div class="form-actions">
              <button type="button" @click="scriptComposerOpen = false">取消</button>
              <button class="text-action" :disabled="saving || !newScriptContent.trim()">
                {{ saving ? '索引中…' : '上传并索引' }}
              </button>
            </div>
          </form>

          <div class="rail-divider"></div>
          <div class="rail-heading">
            <span>MCP 出口</span>
            <span class="mcp-dot" :class="mcpAlive === null ? 'unknown' : (mcpAlive ? 'on' : 'off')"
                  :title="mcpAlive === null ? '探测中' : (mcpAlive ? '适配器在线 (9091)' : '适配器未启动')"></span>
          </div>
          <div class="mcp-panel">
            <p class="mcp-hint">把你的视频知识库挂给外部 AI 助手（Cursor / Cherry Studio 等支持 Streamable HTTP 的客户端）：</p>
            <button type="button" class="mcp-endpoint" title="点击复制端点" @click="copyText(mcpEndpoint, '端点已复制')">
              {{ mcpEndpoint }}
            </button>
            <button type="button" class="mcp-config" title="点击复制客户端配置" @click="copyText(mcpClientConfig, '客户端配置已复制')">{{ mcpClientConfig }}</button>
            <p class="mcp-hint">将 <code>&lt;令牌&gt;</code> 换成服务端 <code>.env</code> 里 <code>MCP_CLIENT_TOKENS</code> 配置的值；显示离线时先运行 <code>./scripts/dev-up.sh</code>。三个只读工具：列空间 / 跨视频搜证据 / 取原始转写。</p>
          </div>

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
            <div class="header-tools">
              <form class="tag-filter" @submit.prevent="applyTagFilter">
                <input v-model="tagFilter" maxlength="64" placeholder="按标签筛选" aria-label="按标签筛选" />
                <button v-if="activeTag" type="button" class="tag-clear" aria-label="清除标签筛选" @click="clearTagFilter">×</button>
              </form>
              <button type="button" class="subtle-button" :disabled="loading" @click="refreshCurrent">刷新</button>
            </div>
          </header>

          <form class="cross-search" @submit.prevent="askKnowledge">
            <div class="cross-search-row">
              <input
                v-model="searchQuery"
                maxlength="500"
                placeholder="问问这个知识空间，例如：视频里解释缓存击穿的步骤是什么？"
                aria-label="向知识空间提问"
              />
              <button type="submit" class="lime-button" :disabled="asking || searching || !searchQuery.trim()">
                {{ asking ? '整理证据中…' : '提问' }}
              </button>
              <button type="button" class="subtle-button evidence-only-button" :disabled="asking || searching || !searchQuery.trim()" @click="searchKnowledge">
                {{ searching ? '检索中…' : '仅搜证据' }}
              </button>
            </div>
            <p class="cross-search-hint">回答会附原文引用；点击视频时间可打开原片回看。</p>
            <p v-if="answerError || searchError" class="cross-search-error" role="alert">{{ answerError || searchError }}</p>
          </form>

          <section v-if="answerResult" class="answer-results" :class="{ 'is-insufficient': answerResult.answerability !== 'SUPPORTED' }" aria-live="polite">
            <header class="answer-results-head">
              <div class="answer-status">
                <span class="answer-status-mark">{{ answerResult.answerability === 'SUPPORTED' ? '有证据支持' : '证据不足' }}</span>
                <span v-if="answerResult.citations?.length">{{ answerResult.citations.length }} 条引用</span>
              </div>
              <button type="button" class="subtle-button" @click="answerResult = null">收起</button>
            </header>
            <p class="answer-copy">{{ answerResult.answer }}</p>
            <ul v-if="answerResult.citations?.length" class="answer-citation-list" aria-label="回答引用">
              <li v-for="(citation, index) in answerResult.citations" :key="citation.segmentId" class="answer-citation">
                <div class="answer-citation-meta">
                  <span class="citation-index">{{ String(index + 1).padStart(2, '0') }}</span>
                  <strong :title="citation.title">{{ citation.title || '未命名来源' }}</strong>
                  <button
                    v-if="citation.mediaId != null"
                    type="button"
                    class="citation-time"
                    :aria-label="`打开 ${citation.title} ${formatMs(citation.startMs)} 的视频证据`"
                    @click="$emit('open-evidence', { mediaId: citation.mediaId, timestampMs: citation.startMs })"
                  >{{ formatMs(citation.startMs) }}–{{ formatMs(citation.endMs) }} ↗</button>
                  <span v-else class="citation-source-type">文本来源</span>
                </div>
                <p v-if="citation.claim" class="citation-claim">{{ citation.claim }}</p>
                <blockquote>{{ citation.quote }}</blockquote>
              </li>
            </ul>
            <ul v-if="answerResult.warnings?.length" class="answer-warnings">
              <li v-for="warning in answerResult.warnings" :key="warning">{{ warning }}</li>
            </ul>
          </section>

          <section v-if="searched" class="search-results" aria-label="跨视频检索结果">
            <header class="search-results-head">
              <span>检索结果</span>
              <button type="button" class="subtle-button" @click="closeSearchResults">收起</button>
            </header>
            <!-- 检索是空间级的：空空间里"无证据"是必然结果，必须提示用户切换空间而不是让他误判检索坏了 -->
            <p v-if="searchResults.length === 0" class="search-empty">
              未检索到支持证据。{{ sources.length === 0 ? '当前空间还没有内容源——请检查左侧是否选错了空间（检索只在所选空间内进行）。' : '换一个更贴近视频原话的问法再试。' }}
            </p>
            <ul v-else class="search-hit-list">
              <li v-for="hit in searchResults" :key="hit.segmentId" class="search-hit">
                <div class="search-hit-meta">
                  <strong :title="hit.title">{{ hit.title }}</strong>
                  <span class="search-hit-time">{{ formatMs(hit.startMs) }} – {{ formatMs(hit.endMs) }}</span>
                  <span class="search-hit-kind">{{ hit.sourceType === 'SCRIPT' ? '脚本' : '视频' }}·{{ hit.matchType === 'vector' ? '语义' : '关键词' }}</span>
                </div>
                <p>{{ hit.transcript || hit.ocrText || hit.summary }}</p>
              </li>
            </ul>
          </section>

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
                <p>
                  <span :class="['status-chip', `status-${source.status.toLowerCase()}`]">{{ source.status }}</span>
                  <span>版本 {{ source.currentVersion }}</span>
                  <span>{{ formatDate(source.updatedAt) }}</span>
                </p>
                <p v-if="(source.tags?.length || tagEditorId === source.id)" class="source-tags">
                  <span v-for="tag in source.tags || []" :key="tag" class="tag-chip">
                    {{ tag }}
                    <button type="button" class="tag-remove" :aria-label="`移除标签 ${tag}`" :disabled="saving" @click="removeTag(source, tag)">×</button>
                  </span>
                  <button
                    v-if="tagEditorId !== source.id"
                    type="button"
                    class="tag-add"
                    :disabled="saving"
                    @click="toggleTagEditor(source)"
                  >+ 标签</button>
                </p>
                <form v-if="tagEditorId === source.id" class="tag-editor" @submit.prevent="commitTagEditor(source)">
                  <input
                    v-model="newTagDraft"
                    maxlength="64"
                    placeholder="输入标签，回车确认"
                    aria-label="新标签"
                    autofocus
                  />
                  <button type="button" class="subtle-button" @click="closeTagEditor">取消</button>
                  <button type="submit" class="subtle-button text-action" :disabled="saving || !newTagDraft.trim()">添加</button>
                </form>
                <div v-if="linksPanelId === source.id" class="links-panel" :aria-busy="linksBusy">
                  <div class="links-actions">
                    <button
                      type="button"
                      class="subtle-button"
                      :disabled="linksBusy"
                      @click="suggestLinks(source)"
                    >{{ linksBusy ? '匹配中…' : '生成关联建议' }}</button>
                    <span class="links-hint">按语义相似度推荐视频片段配对；建议≠事实，确认后才生效。</span>
                  </div>
                  <p v-if="linkNotice" class="links-notice" role="status">{{ linkNotice }}</p>
                  <p v-if="!linksBusy && links.length === 0" class="links-empty">暂无关联记录。</p>
                  <ul v-else class="link-list">
                    <li v-for="link in links" :key="link.id" class="link-row">
                      <div class="link-head">
                        <span :class="['status-chip', `link-${link.status.toLowerCase()}`]">{{ linkStatusText(link.status) }}</span>
                        <strong :title="link.targetTitle">{{ link.targetTitle }}</strong>
                        <span v-if="link.targetStartMs != null" class="link-time">{{ formatMs(link.targetStartMs) }} – {{ formatMs(link.targetEndMs) }}</span>
                        <span v-if="link.confidence != null" class="link-score">{{ (link.confidence * 100).toFixed(0) }}%</span>
                        <template v-if="link.status === 'SUGGESTED'">
                          <button type="button" class="subtle-button" :disabled="linksBusy" @click="confirmLink(link)">确认</button>
                          <button type="button" class="subtle-button" :disabled="linksBusy" @click="rejectLink(link)">拒绝</button>
                        </template>
                      </div>
                      <p class="link-pair">
                        <span class="link-side">稿</span>{{ link.sourceExcerpt }}
                        <span class="link-side">视频</span>{{ link.targetExcerpt }}
                      </p>
                    </li>
                  </ul>
                </div>
              </div>
              <button
                v-if="source.sourceType === 'SCRIPT'"
                type="button"
                class="source-move"
                @click="toggleLinksPanel(source)"
              >关联{{ linksPanelId === source.id ? ' ▴' : '' }}</button>
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
defineEmits(['request-login', 'open-evidence'])

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
const tagFilter = ref('')
const tagEditorId = ref(null)
const newTagDraft = ref('')
const searchQuery = ref('')
const asking = ref(false)
const answerResult = ref(null)
const answerError = ref('')
const searching = ref(false)
const searched = ref(false)
const searchResults = ref([])
const searchError = ref('')
const ingestPath = ref('')
const ingestBusy = ref(false)
const ingestPlan = ref(null)
const ingestError = ref('')
const scriptComposerOpen = ref(false)
const newScriptTitle = ref('')
const newScriptContent = ref('')
const mcpAlive = ref(null)
const mcpEndpoint = computed(() => `${location.protocol}//${location.hostname}:9091/mcp`)
const mcpClientConfig = computed(() =>
  JSON.stringify({ mcpServers: { 'dovideo-knowledge': { url: mcpEndpoint.value, headers: { Authorization: 'Bearer <令牌>' } } } }, null, 2))

function probeMcpAdapter() {
  mcpAlive.value = null
  fetch(mcpEndpoint.value, { method: 'GET' })
    .then(() => { mcpAlive.value = true })   // 401/405 都说明适配器在响应
    .catch(() => { mcpAlive.value = false })
}

async function copyText(text, message) {
  try {
    await navigator.clipboard.writeText(text)
    notice.value = message
  } catch {
    notice.value = '复制失败，请手动选择复制'
  }
}

const linksPanelId = ref(null)
const links = ref([])
const linksBusy = ref(false)
const linkNotice = ref('')

const ingestActions = computed(() => {
  if (!ingestPlan.value?.plan) return []
  try {
    return JSON.parse(ingestPlan.value.plan).actions || []
  } catch {
    return []
  }
})

const selectedSpace = computed(() => spaces.value.find(space => space.id === selectedSpaceId.value) || null)
const selectedCollection = computed(() => collections.value.find(collection => collection.id === selectedCollectionId.value) || null)
const collectionParent = computed(() => collections.value.find(collection => collection.id === collectionParentId.value) || null)
const sourceCount = computed(() => sources.value.length)
const activeTag = computed(() => tagFilter.value.trim())

watch(() => props.user?.id, async userId => {
  resetState()
  if (userId) {
    probeMcpAdapter()
    await loadSpaces()
  }
}, { immediate: true })

function resetState() {
  spaces.value = []
  collections.value = []
  sources.value = []
  selectedSpaceId.value = null
  selectedCollectionId.value = null
  error.value = ''
  notice.value = ''
  answerResult.value = null
  answerError.value = ''
  searchResults.value = []
  searched.value = false
  tagFilter.value = ''
  closeTagEditor()
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
  const tag = tagFilter.value.trim()
  const sourcePath = new URLSearchParams({ spaceId: String(spaceId) })
  if (collectionId !== null) sourcePath.set('collectionId', String(collectionId))
  if (tag) sourcePath.set('tag', tag)
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
  clearKnowledgeResponses()
  closeCollectionComposer()
  await refreshCurrent()
}

async function selectCollection(collectionId) {
  if (collectionId === selectedCollectionId.value) return
  selectedCollectionId.value = collectionId
  clearKnowledgeResponses()
  closeCollectionComposer()
  await refreshCurrent()
}

function clearKnowledgeResponses() {
  answerResult.value = null
  answerError.value = ''
  searchResults.value = []
  searchError.value = ''
  searched.value = false
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

function applyTagFilter() {
  tagFilter.value = tagFilter.value.trim()
  refreshCurrent()
}

async function searchKnowledge() {
  const query = searchQuery.value.trim()
  if (!query || !selectedSpaceId.value) return
  searching.value = true
  searchError.value = ''
  answerError.value = ''
  answerResult.value = null
  try {
    const hits = await request('/knowledge/search', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ spaceId: selectedSpaceId.value, query, topK: 8 })
    })
    searchResults.value = hits
    searched.value = true
  } catch (cause) {
    searchError.value = cause.message || '跨视频检索失败'
  } finally {
    searching.value = false
  }
}

async function askKnowledge() {
  const query = searchQuery.value.trim()
  if (!query || !selectedSpaceId.value) return
  asking.value = true
  answerError.value = ''
  searchError.value = ''
  answerResult.value = null
  searched.value = false
  try {
    answerResult.value = await request('/knowledge/ask', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ spaceId: selectedSpaceId.value, query, topK: 8, strategy: 'hybrid' })
    })
  } catch (cause) {
    answerError.value = cause.message || '知识库回答失败，请稍后重试'
  } finally {
    asking.value = false
  }
}

function closeSearchResults() {
  searched.value = false
  searchResults.value = []
}

async function runIngest(dryRun) {
  const rootPath = ingestPath.value.trim()
  if (!rootPath || !selectedSpaceId.value) return
  ingestBusy.value = true
  ingestError.value = ''
  try {
    ingestPlan.value = await request('/knowledge/ingest/scan', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        rootPath,
        spaceId: selectedSpaceId.value,
        collectionId: selectedCollectionId.value,
        dryRun
      })
    })
    if (!dryRun) {
      notice.value = '目录导入已应用'
      await refreshCurrent()
    }
  } catch (cause) {
    ingestError.value = cause.message || '目录导入失败'
  } finally {
    ingestBusy.value = false
  }
}

async function applyIngest() {
  await runIngest(false)
}

async function createScript() {
  if (!newScriptContent.value.trim()) return
  saving.value = true
  error.value = ''
  notice.value = ''
  try {
    const created = await request('/knowledge/sources/script', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        title: newScriptTitle.value,
        content: newScriptContent.value,
        spaceId: selectedSpaceId.value
      })
    })
    scriptComposerOpen.value = false
    newScriptTitle.value = ''
    newScriptContent.value = ''
    notice.value = `脚本「${created.title}」已索引（${created.status}），现在可跨视频检索。`
    await loadCurrentSpace()
  } catch (cause) {
    error.value = cause.message || '脚本上传失败'
  } finally {
    saving.value = false
  }
}

async function toggleLinksPanel(source) {
  if (linksPanelId.value === source.id) {
    linksPanelId.value = null
    return
  }
  linksPanelId.value = source.id
  links.value = []
  linkNotice.value = ''
  await loadLinks(source)
}

async function loadLinks(source) {
  linksBusy.value = true
  try {
    links.value = await request(`/knowledge/sources/${source.id}/links`)
  } catch (cause) {
    linkNotice.value = cause.message || '关联记录读取失败'
  } finally {
    linksBusy.value = false
  }
}

async function suggestLinks(source) {
  linksBusy.value = true
  linkNotice.value = ''
  try {
    const created = await request(`/knowledge/sources/${source.id}/links/suggest`, { method: 'POST' })
    linkNotice.value = created > 0
      ? `已生成 ${created} 条建议，请逐条核对后确认。`
      : '没有新的可建议配对（内容不相似或均已处理）。'
    await loadLinks(source)
  } catch (cause) {
    linkNotice.value = cause.message || '关联建议生成失败'
    linksBusy.value = false
  }
}

async function confirmLink(link) {
  linksBusy.value = true
  try {
    await request(`/knowledge/links/${link.id}/confirm`, { method: 'POST' })
    link.status = 'CONFIRMED'
  } catch (cause) {
    linkNotice.value = cause.message || '确认失败'
  } finally {
    linksBusy.value = false
  }
}

async function rejectLink(link) {
  linksBusy.value = true
  try {
    await request(`/knowledge/links/${link.id}/reject`, { method: 'POST' })
    link.status = 'REJECTED'
  } catch (cause) {
    linkNotice.value = cause.message || '拒绝失败'
  } finally {
    linksBusy.value = false
  }
}

function linkStatusText(status) {
  return { SUGGESTED: '建议', CONFIRMED: '已确认', REJECTED: '已拒绝' }[status] || status
}

function formatMs(value) {
  const totalSeconds = Math.max(0, Math.floor((value || 0) / 1000))
  const minutes = Math.floor(totalSeconds / 60)
  const seconds = String(totalSeconds % 60).padStart(2, '0')
  return `${minutes}:${seconds}`
}

async function clearTagFilter() {
  if (!tagFilter.value) return
  tagFilter.value = ''
  await refreshCurrent()
}

function toggleTagEditor(source) {
  tagEditorId.value = source.id
  newTagDraft.value = ''
}

function closeTagEditor() {
  tagEditorId.value = null
  newTagDraft.value = ''
}

async function commitTagEditor(source) {
  const tag = newTagDraft.value.trim()
  if (!tag) return
  await saveTags(source, [...(source.tags || []), tag])
  closeTagEditor()
}

async function removeTag(source, tag) {
  await saveTags(source, (source.tags || []).filter(candidate => candidate !== tag))
}

async function saveTags(source, tags) {
  saving.value = true
  error.value = ''
  try {
    const updated = await request(`/knowledge/sources/${source.id}/tags`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ tags })
    })
    sources.value = sources.value.map(candidate => (candidate.id === updated.id ? updated : candidate))
  } catch (cause) {
    error.value = cause.message || '保存标签失败'
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
.rail-form input, .rail-form textarea, .move-tray select { width: 100%; border: 1px solid var(--border-tech); border-radius: 0; background: #090a0d; color: var(--text-main); padding: 8px; outline: none; }
.rail-form input:focus, .rail-form textarea:focus, .move-tray select:focus { border-color: var(--accent-lime); }
.rail-form textarea { resize: vertical; min-height: 96px; font: .78rem/1.5 inherit; }
.mcp-dot { width: 8px; height: 8px; border-radius: 50%; }
.mcp-dot.on { background: var(--accent-lime); box-shadow: 0 0 6px rgba(197,249,70,.8); }
.mcp-dot.off { background: #ff6876; }
.mcp-dot.unknown { background: #888; }
.mcp-panel { display: grid; gap: 7px; }
.mcp-hint { margin: 0; color: var(--text-sub); font: .62rem/1.5 monospace; }
.mcp-hint code { color: var(--accent-lime); }
.mcp-endpoint { width: 100%; text-align: left; padding: 7px 8px; border: 1px solid rgba(197,249,70,.4); background: rgba(197,249,70,.06); color: var(--accent-lime); font: .66rem/1.3 monospace; cursor: copy; }
.mcp-config { width: 100%; text-align: left; white-space: pre-wrap; word-break: break-all; padding: 8px; border: 1px solid var(--border-tech); background: #090a0d; color: var(--text-main); font: .6rem/1.4 monospace; cursor: copy; }
.links-panel { margin-top: 10px; padding: 10px; border: 1px solid rgba(42,45,53,.9); background: rgba(9,10,13,.6); }
.links-actions { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.links-hint { color: var(--text-sub); font: .65rem/1.4 monospace; }
.links-notice { margin: 6px 0 0; color: var(--accent-lime); font: .68rem/1.4 monospace; }
.links-empty { margin: 8px 0 0; color: var(--text-sub); font: .68rem/1.4 monospace; }
.link-list { list-style: none; margin: 8px 0 0; padding: 0; display: grid; gap: 8px; }
.link-row { padding: 8px 0; border-top: 1px dashed rgba(42,45,53,.9); }
.link-head { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; font-size: .72rem; }
.link-head strong { overflow: hidden; max-width: 220px; text-overflow: ellipsis; white-space: nowrap; }
.link-time, .link-score { color: var(--text-sub); font: .65rem/1 monospace; }
.link-score { color: var(--accent-lime); }
.link-pair { display: flex; flex-wrap: wrap; gap: 6px; margin: 6px 0 0; color: var(--text-sub); font: .7rem/1.5 monospace; }
.link-side { flex: none; padding: 0 5px; border: 1px solid rgba(197,249,70,.35); color: var(--accent-lime); font-size: .6rem; }
.link-suggested { color: #f2bf6b; }
.link-confirmed { color: var(--accent-lime); }
.link-rejected { color: #ff7a6b; }
.form-actions, .move-actions { display: flex; justify-content: flex-end; gap: 8px; }
.form-actions button, .move-actions button { padding: 5px 8px; font-size: .72rem; }
.text-action { color: var(--accent-lime) !important; }
.source-pane { position: relative; min-width: 0; padding: 30px 34px; }
.source-header { display: flex; justify-content: space-between; align-items: flex-start; gap: 20px; padding-bottom: 22px; border-bottom: 1px solid var(--border-tech); }
.source-path { color: var(--text-sub); font: .72rem/1.5 monospace; }
.source-path span { color: var(--accent-lime); margin: 0 5px; }
.source-header h2 { margin-top: 5px; font-size: 1.5rem; line-height: 1.25; }
.subtle-button { padding: 6px 11px; font: .72rem/1 monospace; }
.header-tools { display: flex; align-items: center; gap: 10px; }
.tag-filter { position: relative; display: flex; }
.tag-filter input { width: 168px; border: 1px solid var(--border-tech); border-radius: 0; background: #090a0d; color: var(--text-main); padding: 7px 26px 7px 9px; font: .7rem/1.2 monospace; outline: none; }
.tag-filter input:focus { border-color: var(--accent-lime); }
.tag-clear { position: absolute; right: 2px; top: 0; min-height: 100%; padding: 0 7px; border: 0; background: transparent; color: var(--text-sub); cursor: pointer; font-size: .9rem; }
.tag-clear:hover { color: var(--accent-lime); }
.source-tags { margin-top: 7px; }
.tag-chip { display: inline-flex; align-items: center; gap: 4px; padding: 3px 4px 3px 8px; border: 1px solid rgba(197,249,70,.35); background: rgba(197,249,70,.06); color: var(--text-main); font: .67rem/1.2 monospace; }
.tag-remove { border: 0; background: transparent; color: var(--text-sub); cursor: pointer; font-size: .8rem; line-height: 1; padding: 0 3px; }
.tag-remove:hover:not(:disabled) { color: #ff6876; }
.tag-add { border: 0; background: transparent; color: var(--text-sub); cursor: pointer; font: .67rem/1.2 monospace; padding: 3px 6px; }
.tag-add:hover:not(:disabled) { color: var(--accent-lime); }
.tag-editor { display: flex; gap: 7px; margin-top: 8px; max-width: 340px; }
.tag-editor input { flex: 1; border: 1px solid var(--border-tech); border-radius: 0; background: #090a0d; color: var(--text-main); padding: 7px 9px; font: .72rem/1.2 monospace; outline: none; }
.tag-editor input:focus { border-color: var(--accent-lime); }
.cross-search { margin: 0 0 20px; }
.cross-search-row { display: grid; grid-template-columns: minmax(0, 1fr) auto auto; align-items: center; gap: 9px; }
.cross-search-row input { min-width: 0; border: 1px solid var(--border-tech); border-radius: 0; background: #090a0d; color: var(--text-main); padding: 10px 12px; font: .8rem/1.4 'Noto Sans SC', sans-serif; outline: none; }
.cross-search-row input:focus { border-color: var(--accent-lime); }
.cross-search .lime-button { min-height: 42px; padding: 0 18px; font-size: .8rem; }
.evidence-only-button { min-height: 42px; padding: 0 13px; }
.cross-search-hint { margin: 7px 0 0; color: var(--text-sub); font: .66rem/1.5 monospace; }
.cross-search-error { margin: 8px 0 0; color: #ff6876; font: .72rem/1.5 monospace; }
.answer-results { margin: 0 0 24px; border: 1px solid rgba(197,249,70,.35); background: rgba(197,249,70,.035); }
.answer-results.is-insufficient { border-color: rgba(242,191,107,.45); background: rgba(242,191,107,.035); }
.answer-results-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 9px 13px; border-bottom: 1px solid rgba(197,249,70,.22); }
.is-insufficient .answer-results-head { border-color: rgba(242,191,107,.24); }
.answer-status { display: flex; align-items: center; flex-wrap: wrap; gap: 11px; color: var(--text-sub); font: .67rem/1.4 monospace; }
.answer-status-mark { color: var(--accent-lime); letter-spacing: .06em; }
.is-insufficient .answer-status-mark { color: #f2bf6b; }
.answer-copy { margin: 0; padding: 16px 14px; color: var(--text-main); font-size: .91rem; line-height: 1.85; white-space: pre-wrap; }
.answer-citation-list { list-style: none; margin: 0; padding: 0 14px 12px; display: grid; gap: 9px; }
.answer-citation { padding: 11px 12px; border-top: 1px solid rgba(42,45,53,.9); background: rgba(5,6,8,.3); }
.answer-citation-meta { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; color: var(--text-sub); font: .68rem/1.4 monospace; }
.answer-citation-meta strong { min-width: 0; max-width: min(52ch, 100%); overflow-wrap: anywhere; color: var(--text-main); font: 700 .74rem/1.4 'Noto Sans SC', sans-serif; }
.citation-index { color: var(--accent-lime); }
.citation-time { min-height: 40px; margin-left: auto; padding: 3px 7px; border: 1px solid rgba(197,249,70,.35); background: transparent; color: var(--accent-lime); font: .68rem/1 monospace; cursor: pointer; touch-action: manipulation; }
@media (hover:hover) { .citation-time:hover { background: rgba(197,249,70,.08); } }
.citation-time:active { transform: scale(.96); }
.citation-time:focus-visible { outline: 2px solid var(--accent-lime); outline-offset: 2px; }
.citation-source-type { margin-left: auto; color: var(--text-sub); }
.citation-claim { margin: 7px 0 0; color: var(--text-main); font-size: .76rem; line-height: 1.65; }
.answer-citation blockquote { margin: 6px 0 0; padding-left: 10px; border-left: 1px solid rgba(197,249,70,.5); color: var(--text-sub); font-size: .74rem; line-height: 1.75; overflow-wrap: anywhere; }
.answer-warnings { margin: 0; padding: 0 26px 13px; color: #f2bf6b; font: .68rem/1.6 monospace; }
.search-results { margin-bottom: 24px; border: 1px solid rgba(197,249,70,.3); background: rgba(197,249,70,.035); }
.search-results-head { display: flex; align-items: center; justify-content: space-between; padding: 10px 13px; border-bottom: 1px solid rgba(197,249,70,.25); color: var(--accent-lime); font: .7rem/1 monospace; letter-spacing: .1em; text-transform: uppercase; }
.search-empty { padding: 16px 13px; color: var(--text-sub); font: .78rem/1.6 monospace; }
.search-hit-list { list-style: none; margin: 0; padding: 0; }
.search-hit { padding: 13px; border-bottom: 1px solid rgba(42,45,53,.8); }
.search-hit:last-child { border-bottom: 0; }
.search-hit-meta { display: flex; flex-wrap: wrap; align-items: baseline; gap: 8px 12px; margin-bottom: 6px; }
.search-hit-meta strong { overflow: hidden; max-width: 340px; text-overflow: ellipsis; white-space: nowrap; font-size: .82rem; }
.search-hit-time { color: var(--accent-lime); font: .7rem/1 monospace; }
.search-hit-kind { padding: 2px 6px; border: 1px solid rgba(197,249,70,.35); color: var(--text-sub); font: .62rem/1.2 monospace; }
.search-hit p { margin: 0; color: var(--text-sub); font-size: .8rem; line-height: 1.7; }
.ingest-error { margin: 6px 0 0; color: #ff6876; font: .65rem/1.4 monospace; }
.ingest-plan { margin: 2px 3px 10px; padding: 8px; border: 1px solid var(--border-tech); background: rgba(5,6,8,.35); }
.ingest-plan-head { margin: 0 0 6px; color: var(--text-sub); font: .62rem/1.5 monospace; }
.ingest-plan ul { list-style: none; margin: 0; padding: 0; display: grid; gap: 3px; max-height: 180px; overflow: auto; }
.ingest-plan li { display: flex; align-items: baseline; gap: 6px; font: .65rem/1.4 monospace; }
.ingest-action { min-width: 58px; color: var(--accent-lime); }
.ingest-action.is-deleted { color: #ff6876; }
.ingest-action.is-changed { color: #f2bf6b; }
.ingest-action.is-unchanged { color: var(--text-sub); }
.ingest-action.is-error { color: #ff6876; }
.ingest-path { overflow: hidden; min-width: 0; text-overflow: ellipsis; white-space: nowrap; color: var(--text-main); }
.ingest-plan small { color: #ff6876; }
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
.status-ready { color: var(--accent-lime); }
.status-failed { color: #ff7a6b; }
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
  .header-tools { width: 100%; }
  .cross-search-row { grid-template-columns: minmax(0, 1fr) auto; }
  .cross-search-row input { grid-column: 1 / -1; }
  .cross-search .lime-button, .evidence-only-button { min-height: 40px; }
  .tag-filter { flex: 1; }
  .tag-filter input { width: 100%; }
}
@media (prefers-reduced-motion: reduce) { .icon-action:active, .subtle-button:active, .source-move:active, .lime-button:active, .text-action:active, .citation-time:active { transform: none; } }
</style>
