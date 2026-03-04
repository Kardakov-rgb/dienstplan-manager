/// <reference types="vite/client" />
import { IpcApi } from '../shared/types'

declare global {
  interface Window {
    api: IpcApi & {
      onGenerierungProgress: (cb: (progress: number) => void) => () => void
    }
  }
}
