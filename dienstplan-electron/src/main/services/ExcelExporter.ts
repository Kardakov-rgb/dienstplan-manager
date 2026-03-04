import ExcelJS from 'exceljs'
import { Dienstplan, DienstArt, DienstArtInfo, GesamtStatistik } from '../../shared/types'
import log from 'electron-log'

const COLORS = {
  DIENST_24H: 'FFD4E6F1',  // light blue
  VISTEN:     'FFFDE9D9',  // light orange
  DAVINCI:    'FFE8F5E9',  // light green
  OFFEN:      'FFFCE4EC',  // light pink
  HEADER:     'FF7A1D21',  // dark red
  SUBHEADER:  'FFC82285'   // magenta
}

export async function exportDienstplanToExcel(dienstplan: Dienstplan, filePath: string): Promise<void> {
  log.info(`Exporting Dienstplan ${dienstplan.name} to ${filePath}`)
  const wb = new ExcelJS.Workbook()
  wb.creator = 'Dienstplan-Manager'
  wb.created = new Date()

  // --- Sheet 1: Calendar overview ---
  const sheet = wb.addWorksheet('Dienstplan', {
    pageSetup: { orientation: 'landscape', fitToPage: true, fitToWidth: 1 }
  })

  sheet.columns = [
    { header: 'Datum', key: 'datum', width: 14 },
    { header: 'Tag', key: 'tag', width: 6 },
    { header: 'Dienstart', key: 'art', width: 16 },
    { header: 'Person', key: 'person', width: 24 },
    { header: 'Status', key: 'status', width: 14 },
    { header: 'Bemerkung', key: 'bemerkung', width: 30 }
  ]

  // Style header row
  const headerRow = sheet.getRow(1)
  headerRow.eachCell((cell) => {
    cell.fill = { type: 'pattern', pattern: 'solid', fgColor: { argb: COLORS.HEADER } }
    cell.font = { bold: true, color: { argb: 'FFFFFFFF' } }
    cell.alignment = { vertical: 'middle', horizontal: 'center' }
  })
  headerRow.height = 20

  const TAGE = ['So', 'Mo', 'Di', 'Mi', 'Do', 'Fr', 'Sa']

  for (const dienst of dienstplan.dienste) {
    const date = new Date(dienst.datum + 'T00:00:00')
    const tagName = TAGE[date.getDay()]
    const artInfo = DienstArtInfo[dienst.art]

    const row = sheet.addRow({
      datum: dienst.datum,
      tag: tagName,
      art: artInfo.vollName,
      person: dienst.personName ?? '– offen –',
      status: dienst.status,
      bemerkung: dienst.bemerkung ?? ''
    })

    const bgColor = dienst.personId ? COLORS[dienst.art] ?? 'FFFFFFFF' : COLORS.OFFEN
    row.eachCell((cell) => {
      cell.fill = { type: 'pattern', pattern: 'solid', fgColor: { argb: bgColor } }
      cell.border = {
        top: { style: 'thin', color: { argb: 'FFD0D0D0' } },
        bottom: { style: 'thin', color: { argb: 'FFD0D0D0' } },
        left: { style: 'thin', color: { argb: 'FFD0D0D0' } },
        right: { style: 'thin', color: { argb: 'FFD0D0D0' } }
      }
    })
  }

  // Auto-filter
  sheet.autoFilter = { from: 'A1', to: 'F1' }

  // --- Sheet 2: Person summary ---
  const summarySheet = wb.addWorksheet('Zusammenfassung')
  summarySheet.columns = [
    { header: 'Person', key: 'person', width: 24 },
    { header: '24h-Dienste', key: 'h24', width: 14 },
    { header: 'Visitendienste', key: 'visten', width: 16 },
    { header: 'DaVinci-Dienste', key: 'davinci', width: 16 },
    { header: 'Gesamt', key: 'gesamt', width: 12 }
  ]

  const summaryHeader = summarySheet.getRow(1)
  summaryHeader.eachCell((cell) => {
    cell.fill = { type: 'pattern', pattern: 'solid', fgColor: { argb: COLORS.SUBHEADER } }
    cell.font = { bold: true, color: { argb: 'FFFFFFFF' } }
    cell.alignment = { horizontal: 'center' }
  })
  summaryHeader.height = 20

  // Aggregate per person
  const personMap = new Map<string, { h24: number; visten: number; davinci: number }>()
  for (const d of dienstplan.dienste) {
    if (!d.personName) continue
    if (!personMap.has(d.personName)) personMap.set(d.personName, { h24: 0, visten: 0, davinci: 0 })
    const stats = personMap.get(d.personName)!
    if (d.art === DienstArt.DIENST_24H) stats.h24++
    else if (d.art === DienstArt.VISTEN) stats.visten++
    else if (d.art === DienstArt.DAVINCI) stats.davinci++
  }

  for (const [name, stats] of personMap) {
    summarySheet.addRow({
      person: name,
      h24: stats.h24,
      visten: stats.visten,
      davinci: stats.davinci,
      gesamt: stats.h24 + stats.visten + stats.davinci
    })
  }

  await wb.xlsx.writeFile(filePath)
  log.info('Excel export completed:', filePath)
}

export async function exportStatistikToExcel(
  statistik: GesamtStatistik,
  monatVon: string,
  monatBis: string,
  filePath: string
): Promise<void> {
  const wb = new ExcelJS.Workbook()
  wb.creator = 'Dienstplan-Manager'

  // --- Overview sheet ---
  const sheet = wb.addWorksheet('Übersicht')
  sheet.addRow(['Zeitraum', `${monatVon} bis ${monatBis}`])
  sheet.addRow(['Gesamt Dienste', statistik.gesamtDienste])
  sheet.addRow(['Zugewiesen', statistik.zugewieseneDienste])
  sheet.addRow(['Offen', statistik.offeneDienste])
  sheet.addRow(['Zuweisungsgrad', `${(statistik.zuweisungsgrad * 100).toFixed(1)}%`])
  sheet.addRow(['Wunscherfüllung', `${(statistik.wunschErfuellungsQuote * 100).toFixed(1)}%`])

  // --- Person distribution sheet ---
  const personSheet = wb.addWorksheet('Dienst-Verteilung')
  personSheet.addRow(['Person', 'Soll', 'Ist', '24h', 'Visten', 'DaVinci'])
  for (const p of statistik.personStatistiken) {
    personSheet.addRow([p.personName, p.sollDienste, p.istDienste, p.dienste24h, p.diensteVisten, p.diensteDAVinci])
  }

  // --- Open shifts sheet ---
  const offenSheet = wb.addWorksheet('Offene Dienste')
  offenSheet.addRow(['Datum', 'Dienstart', 'Dienstplan'])
  for (const d of statistik.offeneDiensteListe) {
    offenSheet.addRow([d.datum, DienstArtInfo[d.dienstArt].vollName, d.dienstplanName])
  }

  // --- Conflicts sheet ---
  const konfliktSheet = wb.addWorksheet('Konflikte')
  konfliktSheet.addRow(['Person', 'Datum', 'Dienste'])
  for (const k of statistik.konflikte) {
    konfliktSheet.addRow([k.personName, k.datum, k.dienste.join(', ')])
  }

  await wb.xlsx.writeFile(filePath)
}

export async function importWuenscheFromExcel(
  filePath: string,
  monat: string,
  personMap: Map<string, number>
): Promise<{ personId: number; personName: string; datum: string; monat: string; typ: string }[]> {
  const wb = new ExcelJS.Workbook()
  await wb.xlsx.readFile(filePath)

  const sheet = wb.worksheets[0]
  const results: { personId: number; personName: string; datum: string; monat: string; typ: string }[] = []

  sheet.eachRow((row, rowIndex) => {
    if (rowIndex === 1) return // skip header
    const personName = String(row.getCell(1).value ?? '').trim()
    const datumVal = row.getCell(2).value
    const typVal = String(row.getCell(3).value ?? '').trim().toUpperCase()

    if (!personName || !datumVal) return

    // Resolve person
    const personId = personMap.get(personName)
    if (!personId) return

    // Parse date
    let datum: string
    if (datumVal instanceof Date) {
      datum = datumVal.toISOString().split('T')[0]
    } else {
      datum = String(datumVal).trim()
      // Basic validation: YYYY-MM-DD
      if (!/^\d{4}-\d{2}-\d{2}$/.test(datum)) return
    }

    // Map typ
    let typ: string
    if (typVal === 'U' || typVal === 'URLAUB') typ = 'URLAUB'
    else if (typVal === 'F' || typVal === 'FREIWUNSCH') typ = 'FREIWUNSCH'
    else if (typVal === 'D' || typVal === 'DIENSTWUNSCH') typ = 'DIENSTWUNSCH'
    else return

    results.push({ personId, personName, datum, monat, typ })
  })

  return results
}
