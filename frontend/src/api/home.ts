import { get } from './http'
import type { HomeData } from '@/types/api'

/** 我的主页：下一个节点、学期赛程带、我参加的比赛、待处理、推荐队友 */
export const getHome = () => get<HomeData>('/home')
