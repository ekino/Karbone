/*
 * Copyright (c) 2026 ekino (https://www.ekino.com/)
 */
package com.ekino.oss.karbone.model

/** The `convertTo` field: a plain format name or a format with typed options. */
public sealed interface OutputFormat {
  public val formatName: String

  /**
   * Any format Carbone supports without options: docx, xlsx, odt, html, txt, epub, md, svg, webp...
   */
  public data class Simple(override val formatName: String) : OutputFormat

  public data class Pdf(val options: PdfOptions = PdfOptions()) : OutputFormat {
    override val formatName: String
      get() = "pdf"
  }

  public data class Jpg(val options: ImageOptions = ImageOptions()) : OutputFormat {
    override val formatName: String
      get() = "jpg"
  }

  public data class Png(val options: ImageOptions = ImageOptions()) : OutputFormat {
    override val formatName: String
      get() = "png"
  }

  public data class Csv(val options: CsvOptions = CsvOptions()) : OutputFormat {
    override val formatName: String
      get() = "csv"
  }

  public companion object {
    @JvmField public val PDF: OutputFormat = Pdf()
    @JvmField public val DOCX: OutputFormat = Simple("docx")
    @JvmField public val XLSX: OutputFormat = Simple("xlsx")
    @JvmField public val PPTX: OutputFormat = Simple("pptx")
    @JvmField public val ODT: OutputFormat = Simple("odt")
    @JvmField public val ODS: OutputFormat = Simple("ods")
    @JvmField public val ODP: OutputFormat = Simple("odp")
    @JvmField public val HTML: OutputFormat = Simple("html")
    @JvmField public val TXT: OutputFormat = Simple("txt")
    @JvmField public val MD: OutputFormat = Simple("md")
    @JvmField public val EPUB: OutputFormat = Simple("epub")
    @JvmField public val SVG: OutputFormat = Simple("svg")
    @JvmField public val WEBP: OutputFormat = Simple("webp")

    @JvmStatic public fun pdf(version: PdfVersion): Pdf = Pdf(PdfOptions(version = version))

    public fun pdf(configure: PdfOptions.Builder.() -> Unit): Pdf =
      Pdf(PdfOptions.Builder().apply(configure).build())
  }
}

/** PDF `formatOptions`. `null` leaves the Carbone default. */
public data class PdfOptions(
  val version: PdfVersion? = null,
  val encryptFile: Boolean? = null,
  val documentOpenPassword: String? = null,
  val permissionPassword: String? = null,
  val restrictPermissions: Boolean? = null,
  val printing: PdfPrinting? = null,
  val changes: PdfChanges? = null,
  /** Legacy single centered watermark. Prefer [watermarks]. */
  val watermark: String? = null,
  val watermarks: List<Watermark> = emptyList(),
  val pdfUaCompliance: Boolean? = null,
  val useTaggedPdf: Boolean? = null,
  /** e.g. "1-3". */
  val pageRange: String? = null,
  val useLosslessCompression: Boolean? = null,
  val reduceImageResolution: Boolean? = null,
  val maxImageResolution: ImageResolution? = null,
  val exportFormFields: Boolean? = null,
  val exportNotes: Boolean? = null,
) {
  public class Builder {
    public var version: PdfVersion? = null
    public var encryptFile: Boolean? = null
    public var documentOpenPassword: String? = null
    public var permissionPassword: String? = null
    public var restrictPermissions: Boolean? = null
    public var printing: PdfPrinting? = null
    public var changes: PdfChanges? = null
    public var watermark: String? = null
    public val watermarks: MutableList<Watermark> = mutableListOf()
    public var pdfUaCompliance: Boolean? = null
    public var useTaggedPdf: Boolean? = null
    public var pageRange: String? = null
    public var useLosslessCompression: Boolean? = null
    public var reduceImageResolution: Boolean? = null
    public var maxImageResolution: ImageResolution? = null
    public var exportFormFields: Boolean? = null
    public var exportNotes: Boolean? = null

    public fun watermark(text: String, configure: Watermark.Builder.() -> Unit = {}): Builder =
      apply {
        watermarks += Watermark.Builder(text).apply(configure).build()
      }

    public fun build(): PdfOptions =
      PdfOptions(
        version,
        encryptFile,
        documentOpenPassword,
        permissionPassword,
        restrictPermissions,
        printing,
        changes,
        watermark,
        watermarks.toList(),
        pdfUaCompliance,
        useTaggedPdf,
        pageRange,
        useLosslessCompression,
        reduceImageResolution,
        maxImageResolution,
        exportFormFields,
        exportNotes,
      )
  }

  public companion object {
    public const val MAX_WATERMARKS: Int = 5
  }
}

/** `SelectPdfVersion`. */
public enum class PdfVersion(public val code: Int) {
  PDF_1_6_DEFAULT(0),
  PDF_A_1(1),
  PDF_A_2(2),
  PDF_A_3(3),
  PDF_1_5(15),
  PDF_1_6(16),
}

public enum class PdfPrinting(public val code: Int) {
  NOT_PERMITTED(0),
  LOW_RESOLUTION(1),
  MAXIMUM(2),
}

public enum class PdfChanges(public val code: Int) {
  NONE(0),
  INSERT_DELETE_ROTATE(1),
  FILL_FORMS(2),
  COMMENT(3),
  ANY_EXCEPT_EXTRACT(4),
}

public enum class ImageResolution(public val dpi: Int) {
  DPI_75(75),
  DPI_150(150),
  DPI_300(300),
  DPI_600(600),
  DPI_1200(1200),
}

public enum class WatermarkAnchor(public val wire: String) {
  TOP_LEFT("topLeft"),
  TOP("top"),
  TOP_RIGHT("topRight"),
  LEFT("left"),
  CENTER("center"),
  RIGHT("right"),
  BOTTOM_LEFT("bottomLeft"),
  BOTTOM("bottom"),
  BOTTOM_RIGHT("bottomRight"),
}

/** One entry of `Watermarks`. `text` supports `{#PAGE_NUMBER}` and `{#PAGE_TOTAL}`. */
public data class Watermark(
  val text: String,
  val anchor: WatermarkAnchor? = null,
  val offsetX: Double? = null,
  val offsetY: Double? = null,
  val rotation: Double? = null,
  /** `#RGB` or `#RRGGBB`. */
  val color: String? = null,
  val size: Double? = null,
  /** 0..1 */
  val opacity: Double? = null,
  val font: String? = null,
  val fromPage: Int? = null,
  val toPage: Int? = null,
) {
  public class Builder(private val text: String) {
    public var anchor: WatermarkAnchor? = null
    public var offsetX: Double? = null
    public var offsetY: Double? = null
    public var rotation: Double? = null
    public var color: String? = null
    public var size: Double? = null
    public var opacity: Double? = null
    public var font: String? = null
    public var fromPage: Int? = null
    public var toPage: Int? = null

    public fun build(): Watermark =
      Watermark(
        text,
        anchor,
        offsetX,
        offsetY,
        rotation,
        color,
        size,
        opacity,
        font,
        fromPage,
        toPage,
      )
  }
}

/** JPG / PNG `formatOptions`. */
public data class ImageOptions(
  val pixelWidth: Int? = null,
  val pixelHeight: Int? = null,
  val colorMode: ColorMode? = null,
  /** JPG only, 1..100. */
  val quality: Int? = null,
  /** PNG only, 0..9. */
  val compression: Int? = null,
  /** PNG only. */
  val interlaced: Boolean? = null,
  /** PNG only. */
  val translucent: Boolean? = null,
)

public enum class ColorMode(public val code: Int) {
  COLOR(0),
  GREYSCALE(1),
}

/** CSV `formatOptions`. `characterSet` is a LibreOffice charset code, e.g. "76" for UTF-8. */
public data class CsvOptions(
  val fieldSeparator: String? = null,
  val textDelimiter: String? = null,
  val characterSet: String? = null,
)
