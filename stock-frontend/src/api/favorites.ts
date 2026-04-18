import request from '@/utils/request'

export interface FavoriteStock {
  id: number
  symbol: string
  name: string
  exchange: string
  industry: string
  currentPrice?: number
  changePrice?: number
  changePercent?: number
}

export const getFavorites = () => {
  return request.get('/favorites')
}

export const addFavorite = (stockId: number) => {
  return request.post(`/favorites/${stockId}`)
}

export const removeFavorite = (stockId: number) => {
  return request.delete(`/favorites/${stockId}`)
}
