import { get, put } from './http'
import type { HomeData, HomeLayout } from '@/types/api'

/** 我的主页：下一个节点、学期赛程带、我参加的比赛、待处理、推荐队友 */
export const getHome = () => get<HomeData>('/home')

/** 主页自定义：面板顺序、隐藏的面板、日历栏宽窄；没设置过返回默认 */
export const getHomeLayout = () => get<HomeLayout>('/home/layout')

/** 返回整理后的结果（不认识的面板丢掉、漏掉的补上、待你处理不能隐藏） */
export const saveHomeLayout = (layout: HomeLayout) => put<HomeLayout>('/home/layout', layout)
