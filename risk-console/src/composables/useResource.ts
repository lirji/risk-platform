import { ref } from 'vue'

export function useResource<T>(loader: () => Promise<T>) {
  const data = ref<T | null>(null)
  const loading = ref(false)
  const error = ref('')
  const load = async () => {
    loading.value = true; error.value = ''
    try { data.value = await loader() }
    catch (failure) { error.value = failure instanceof Error ? failure.message : '加载失败' }
    finally { loading.value = false }
  }
  return { data, loading, error, load }
}
