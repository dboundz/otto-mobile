package to.ottomot.driftd

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class GarageCarBrandLogoVariant(
    val slug: String,
    val label: String,
)

data class GarageCarBrand(
    val id: String,
    val name: String,
    val defaultLogoSlug: String? = null,
    val logoVariants: List<GarageCarBrandLogoVariant>? = null,
) {
    val resolvedDefaultLogoSlug: String?
        get() = defaultLogoSlug?.takeIf { it.isNotBlank() } ?: id

    val hasLogoPickerOptions: Boolean
        get() = !logoVariants.isNullOrEmpty()

    fun logoPickerOptions(): List<Pair<String, String>> {
        val options = mutableListOf<Pair<String, String>>()
        resolvedDefaultLogoSlug?.let { slug -> options.add(slug to name) }
        logoVariants.orEmpty().forEach { variant ->
            if (options.none { it.first == variant.slug }) {
                options.add(variant.slug to variant.label)
            }
        }
        return options
    }
}

/**
 * Same data as iOS `carBrands.json` / [CarBrandCatalog].
 */
object GarageCarBrandCatalog {
    private var cached: List<GarageCarBrand>? = null

    fun allBrands(context: Context): List<GarageCarBrand> {
        cached?.let { return it }
        val json = context.assets.open("carBrands.json").bufferedReader().use { it.readText() }
        val type = object : TypeToken<List<GarageCarBrand>>() {}.type
        val list = Gson().fromJson<List<GarageCarBrand>>(json, type) ?: emptyList()
        cached = list
        return list
    }

    fun brandForMakeId(context: Context, makeId: String?): GarageCarBrand? {
        val trimmed = makeId?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        return allBrands(context).firstOrNull { it.id == trimmed }
    }

    fun brandMatchingMakeName(context: Context, makeName: String): GarageCarBrand? {
        val trimmed = makeName.trim()
        if (trimmed.isEmpty()) return null
        return allBrands(context).firstOrNull { it.name.equals(trimmed, ignoreCase = true) }
    }

    /** iOS parity: when editing, keep a make string not in the JSON catalog at the top of the picker. */
    fun brandsForEditor(
        context: Context,
        editingMake: String?,
    ): List<GarageCarBrand> {
        val base = allBrands(context).toMutableList()
        val raw = editingMake?.trim().orEmpty()
        if (raw.isNotEmpty() &&
            base.none { it.name.equals(raw, ignoreCase = true) }
        ) {
            base.add(0, GarageCarBrand(id = "legacy-${raw.hashCode()}", name = raw))
        }
        return base
    }
}

object CarBrandLogoCatalog {
    private const val PUBLIC_BASE = "https://otto-motto-upload.s3.us-east-1.amazonaws.com/car-brands"

    /** Bump when S3 car-brand logos are reprocessed so existing installs refresh cached PNGs. */
    const val ASSET_CACHE_VERSION = 1

    private fun cacheBuster(): String = "$ASSET_CACHE_VERSION-${BuildConfig.VERSION_NAME}-${BuildConfig.VERSION_CODE}"

    fun logoUrl(slug: String?): String? {
        val trimmed = slug?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        return "$PUBLIC_BASE/$trimmed.png?v=${cacheBuster()}"
    }

    fun resolvedLogoSlug(
        logoSlug: String?,
        makeId: String?,
        makeName: String,
        context: Context,
    ): String? {
        logoSlug?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        brandForMakeId(context, makeId)?.resolvedDefaultLogoSlug?.let { return it }
        brandMatchingMakeName(context, makeName)?.resolvedDefaultLogoSlug?.let { return it }
        return null
    }

    fun suggestedLogoSlug(
        context: Context,
        makeId: String?,
        model: String,
    ): String? {
        val brand = brandForMakeId(context, makeId) ?: return null
        val trimmedModel = model.trim()
        if (trimmedModel.isEmpty()) return brand.resolvedDefaultLogoSlug
        brand.logoVariants.orEmpty().forEach { variant ->
            if (trimmedModel.contains(variant.label, ignoreCase = true)) {
                return variant.slug
            }
        }
        return brand.resolvedDefaultLogoSlug
    }

    fun defaultLogoSlug(context: Context, makeId: String?): String? =
        brandForMakeId(context, makeId)?.resolvedDefaultLogoSlug

    private fun brandForMakeId(context: Context, makeId: String?) =
        GarageCarBrandCatalog.brandForMakeId(context, makeId)

    private fun brandMatchingMakeName(context: Context, makeName: String) =
        GarageCarBrandCatalog.brandMatchingMakeName(context, makeName)
}
