<script setup>
import { Home, Users, BookOpen, Tags, FolderOpen, FileText, ShieldCheck, ChartColumn, Heart, History, Download,
  Search, Upload, Pencil, GraduationCap, PanelLeftClose, PanelLeftOpen, Menu, X, LogOut, UserRound, ChevronRight,
  ArrowUpRight, CircleCheck, Clock, CircleAlert, Files, Image, FileSpreadsheet, Presentation } from '@lucide/vue'
defineProps({ name: { type: String, default: 'file' }, size: { type: Number, default: 20 } })
const icons = { home: Home, users: Users, course: BookOpen, tags: Tags, category: FolderOpen, file: FileText,
  audit: ShieldCheck, chart: ChartColumn, favorite: Heart, history: History, download: Download, search: Search,
  upload: Upload, edit: Pencil, school: GraduationCap, collapse: PanelLeftClose, expand: PanelLeftOpen, menu: Menu,
  close: X, logout: LogOut, user: UserRound, chevron: ChevronRight, arrow: ArrowUpRight, approved: CircleCheck,
  pending: Clock, rejected: CircleAlert, draft: Files, image: Image, spreadsheet: FileSpreadsheet, presentation: Presentation }
</script>
<template><component :is="icons[name] || FileText" :size="size" :stroke-width="1.7" aria-hidden="true" class="app-icon" /></template>
