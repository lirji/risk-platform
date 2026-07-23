import { useQuery, type QueryKey } from '@tanstack/react-query'

export function useResource<T>(queryKey: QueryKey, queryFn: () => Promise<T>, enabled = true) {
  const query = useQuery({ queryKey, queryFn, enabled, staleTime: 20_000, retry: 1 })
  return {
    data: query.data,
    loading: query.isPending || query.isFetching && !query.data,
    refreshing: query.isFetching && Boolean(query.data),
    error: query.error instanceof Error ? query.error.message : '',
    reload: query.refetch,
  }
}
