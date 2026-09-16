/*
 * Copyright (c) 2026 ekino (https://www.ekino.com/)
 */
package com.ekino.oss.karbone.model

/** The `convertTo` field: a plain format name or a format with typed options. */
public sealed interface OutputFormat {
  /** Carbone format name sent as (or inside) `convertTo`. */
  public val formatName: String

  /**
   * Any format Carbone supports without options: docx, xlsx, odt, html, txt, epub, md, svg, webp...
   */
  public data class Simple(override val formatName: String) : OutputFormat

  /** PDF output. @property options `formatOptions` for the PDF converter. */
  public data class Pdf(val options: PdfOptions = PdfOptions()) : OutputFormat {
    override val formatName: String
      get() = "pdf"
  }

  /** JPG output. @property options `formatOptions` for the image converter. */
  public data class Jpg(val options: ImageOptions = ImageOptions()) : OutputFormat {
    override val formatName: String
      get() = "jpg"
  }

  /** PNG output. @property options `formatOptions` for the image converter. */
  public data class Png(val options: ImageOptions = ImageOptions()) : OutputFormat {
    override val formatName: String
      get() = "png"
  }

  /** CSV output. @property options `formatOptions` for the CSV converter. */
  public data class Csv(val options: CsvOptions = CsvOptions()) : OutputFormat {
    override val formatName: String
      get() = "csv"
  }

  /** Ready-made simple formats, plus constructors for the formats that carry options. */
  public companion object {
    /** PDF with server-default options. */
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

    /** PDF output targeting the given [PdfVersion], other options left at the server default. */
    @JvmStatic public fun pdf(version: PdfVersion): Pdf = Pdf(PdfOptions(version = version))

    /** PDF output configured through the [PdfOptions.Builder] DSL. */
    public fun pdf(configure: PdfOptions.Builder.() -> Unit): Pdf =
      Pdf(PdfOptions.Builder().apply(configure).build())
  }
}

/** PDF `formatOptions`. `null` leaves the Carbone default. */
public data class PdfOptions(
  /** `SelectPdfVersion`; `null` leaves the server default (PDF 1.6). */
  val version: PdfVersion? = null,
  /** `EncryptFile`; requires [documentOpenPassword] and/or [permissionPassword]. */
  val encryptFile: Boolean? = null,
  /** `DocumentOpenPassword`, required to open the document. */
  val documentOpenPassword: String? = null,
  /** `PermissionPassword`, required to lift the restrictions set by [restrictPermissions]. */
  val permissionPassword: String? = null,
  /** `RestrictPermissions`: apply [printing] / [changes] restrictions to the document. */
  val restrictPermissions: Boolean? = null,
  /** `Printing` permission level. */
  val printing: PdfPrinting? = null,
  /** `Changes` permission level. */
  val changes: PdfChanges? = null,
  /** Legacy single centered watermark. Prefer [watermarks]. */
  val watermark: String? = null,
  /** `Watermarks`, at most [MAX_WATERMARKS] entries. */
  val watermarks: List<Watermark> = emptyList(),
  /** `PDFUACompliance`. */
  val pdfUaCompliance: Boolean? = null,
  /** `UseTaggedPDF`. */
  val useTaggedPdf: Boolean? = null,
  /** e.g. "1-3". */
  val pageRange: String? = null,
  /** `UseLosslessCompression`. */
  val useLosslessCompression: Boolean? = null,
  /** `ReduceImageResolution`, used together with [maxImageResolution]. */
  val reduceImageResolution: Boolean? = null,
  /** `MaxImageResolution`, in DPI. */
  val maxImageResolution: ImageResolution? = null,
  /** `ExportFormFields`. */
  val exportFormFields: Boolean? = null,
  /** `ExportNotes`. */
  val exportNotes: Boolean? = null,
) {
  /** Builder for [PdfOptions], exposed by [OutputFormat.pdf] and [RenderOptions.Builder.pdf]. */
  public class Builder {
    /** See [PdfOptions.version]. */
    public var version: PdfVersion? = null
    /** See [PdfOptions.encryptFile]. */
    public var encryptFile: Boolean? = null
    /** See [PdfOptions.documentOpenPassword]. */
    public var documentOpenPassword: String? = null
    /** See [PdfOptions.permissionPassword]. */
    public var permissionPassword: String? = null
    /** See [PdfOptions.restrictPermissions]. */
    public var restrictPermissions: Boolean? = null
    /** See [PdfOptions.printing]. */
    public var printing: PdfPrinting? = null
    /** See [PdfOptions.changes]. */
    public var changes: PdfChanges? = null
    /** See [PdfOptions.watermark]. */
    public var watermark: String? = null
    /** See [PdfOptions.watermarks]. */
    public val watermarks: MutableList<Watermark> = mutableListOf()
    /** See [PdfOptions.pdfUaCompliance]. */
    public var pdfUaCompliance: Boolean? = null
    /** See [PdfOptions.useTaggedPdf]. */
    public var useTaggedPdf: Boolean? = null
    /** See [PdfOptions.pageRange]. */
    public var pageRange: String? = null
    /** See [PdfOptions.useLosslessCompression]. */
    public var useLosslessCompression: Boolean? = null
    /** See [PdfOptions.reduceImageResolution]. */
    public var reduceImageResolution: Boolean? = null
    /** See [PdfOptions.maxImageResolution]. */
    public var maxImageResolution: ImageResolution? = null
    /** See [PdfOptions.exportFormFields]. */
    public var exportFormFields: Boolean? = null
    /** See [PdfOptions.exportNotes]. */
    public var exportNotes: Boolean? = null

    /**
     * Adds one entry to [PdfOptions.watermarks], configured through the [Watermark.Builder] DSL.
     */
    public fun watermark(text: String, configure: Watermark.Builder.() -> Unit = {}): Builder =
      apply {
        watermarks += Watermark.Builder(text).apply(configure).build()
      }

    /** Builds the immutable [PdfOptions]. */
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
    /** Carbone accepts at most this many entries in [watermarks]. */
    public const val MAX_WATERMARKS: Int = 5
  }
}

/** `SelectPdfVersion`; `PDF_1_6_DEFAULT` (0) is the server default. */
public enum class PdfVersion(public val code: Int) {
  PDF_1_6_DEFAULT(0),
  PDF_A_1(1),
  PDF_A_2(2),
  PDF_A_3(3),
  PDF_1_5(15),
  PDF_1_6(16),
}

/** `Printing` permission level. */
public enum class PdfPrinting(public val code: Int) {
  NOT_PERMITTED(0),
  LOW_RESOLUTION(1),
  MAXIMUM(2),
}

/** `Changes` permission level, from no changes allowed to any change except page extraction. */
public enum class PdfChanges(public val code: Int) {
  NONE(0),
  INSERT_DELETE_ROTATE(1),
  FILL_FORMS(2),
  COMMENT(3),
  ANY_EXCEPT_EXTRACT(4),
}

/** `MaxImageResolution`, in DPI. */
public enum class ImageResolution(public val dpi: Int) {
  DPI_75(75),
  DPI_150(150),
  DPI_300(300),
  DPI_600(600),
  DPI_1200(1200),
}

/** `Watermark.anchor` wire value. */
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
  /** Anchor point on the page; `null` leaves the Carbone default. */
  val anchor: WatermarkAnchor? = null,
  /** Horizontal offset from [anchor], in points. */
  val offsetX: Double? = null,
  /** Vertical offset from [anchor], in points. */
  val offsetY: Double? = null,
  /** Rotation, in degrees. */
  val rotation: Double? = null,
  /** `#RGB` or `#RRGGBB`. */
  val color: String? = null,
  /** Font size. */
  val size: Double? = null,
  /** 0..1 */
  val opacity: Double? = null,
  /** Font family name. */
  val font: String? = null,
  /** First page the watermark appears on, 1-based; `null` leaves the Carbone default. */
  val fromPage: Int? = null,
  /** Last page the watermark appears on, 1-based; `null` leaves the Carbone default. */
  val toPage: Int? = null,
) {
  /** Builder for one [Watermark], used through [PdfOptions.Builder.watermark]. */
  public class Builder(private val text: String) {
    /** See [Watermark.anchor]. */
    public var anchor: WatermarkAnchor? = null
    /** See [Watermark.offsetX]. */
    public var offsetX: Double? = null
    /** See [Watermark.offsetY]. */
    public var offsetY: Double? = null
    /** See [Watermark.rotation]. */
    public var rotation: Double? = null
    /** See [Watermark.color]. */
    public var color: String? = null
    /** See [Watermark.size]. */
    public var size: Double? = null
    /** See [Watermark.opacity]. */
    public var opacity: Double? = null
    /** See [Watermark.font]. */
    public var font: String? = null
    /** See [Watermark.fromPage]. */
    public var fromPage: Int? = null
    /** See [Watermark.toPage]. */
    public var toPage: Int? = null

    /** Builds the immutable [Watermark]. */
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
  /** `PixelWidth`; `null` keeps the document's native width. */
  val pixelWidth: Int? = null,
  /** `PixelHeight`; `null` keeps the document's native height. */
  val pixelHeight: Int? = null,
  /** `ColorMode`. */
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

/** `ColorMode` formatOption. */
public enum class ColorMode(public val code: Int) {
  COLOR(0),
  GREYSCALE(1),
}

/** CSV `formatOptions`. `characterSet` is a LibreOffice charset code, e.g. "76" for UTF-8. */
public data class CsvOptions(
  /** `fieldSeparator`; `null` leaves the Carbone default. */
  val fieldSeparator: String? = null,
  /** `textDelimiter`; `null` leaves the Carbone default. */
  val textDelimiter: String? = null,
  val characterSet: String? = null,
)
