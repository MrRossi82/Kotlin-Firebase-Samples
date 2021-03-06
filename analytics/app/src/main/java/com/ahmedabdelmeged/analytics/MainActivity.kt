package com.ahmedabdelmeged.analytics

import android.content.Intent
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.v4.app.FragmentManager
import android.support.v4.app.FragmentPagerAdapter
import android.support.v4.view.ViewPager
import android.support.v7.app.AlertDialog
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import com.google.firebase.analytics.FirebaseAnalytics
import kotlinx.android.synthetic.main.activity_main.*
import java.util.*

/**
 * Activity which displays numerous background images that may be viewed. These background images
 * are shown via {@link ImageFragment}.
 */
class MainActivity : AppCompatActivity() {

    private val TAG = "MainActivity"
    private val KEY_FAVORITE_FOOD = "favorite_food"

    private val IMAGE_INFOS = arrayOf(ImageInfo(R.drawable.favorite, R.string.pattern1_title, R.string.pattern1_id),
            ImageInfo(R.drawable.flash, R.string.pattern2_title, R.string.pattern2_id),
            ImageInfo(R.drawable.face, R.string.pattern3_title, R.string.pattern3_id),
            ImageInfo(R.drawable.whitebalance, R.string.pattern4_title, R.string.pattern4_id))

    /**
     * The [android.support.v4.view.PagerAdapter] that will provide fragments for each image.
     * This uses a [FragmentPagerAdapter], which keeps every loaded fragment in memory.
     */
    private var mImagePagerAdapter: ImagePagerAdapter? = null

    /**
     * The `FirebaseAnalytics` used to record screen views.
     */
    private lateinit var mFirebaseAnalytics: FirebaseAnalytics

    /**
     * The user's favorite food, chosen from a dialog.
     */
    private var mFavoriteFood: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // [START shared_app_measurement]
        // Obtain the FirebaseAnalytics instance.
        mFirebaseAnalytics = FirebaseAnalytics.getInstance(this)
        // [END shared_app_measurement]

        // On first app open, ask the user his/her favorite food. Then set this as a user property
        // on all subsequent opens.
        val userFavoriteFood = getUserFavoriteFood()
        if (userFavoriteFood == null) {
            askFavoriteFood()
        } else {
            setUserFavoriteFood(userFavoriteFood)
        }


        // Create the adapter that will return a fragment for each image.
        mImagePagerAdapter = ImagePagerAdapter(supportFragmentManager, IMAGE_INFOS)

        pager.adapter = mImagePagerAdapter

        // Workaround for AppCompat issue not showing ViewPager titles
        val params = pager_tab_strip.layoutParams as ViewPager.LayoutParams
        params.isDecor = true

        // When the visible image changes, send a screen view hit.
        pager.addOnPageChangeListener(object : ViewPager.SimpleOnPageChangeListener() {
            override fun onPageSelected(position: Int) {
                recordImageView()
                recordScreenView()
            }
        })

        // Send initial screen screen view hit.
        recordImageView()
    }

    public override fun onResume() {
        super.onResume()
        recordScreenView()
    }

    /**
     * Display a dialog prompting the user to pick a favorite food from a list, then record
     * the answer.
     */
    private fun askFavoriteFood() {
        val choices = resources.getStringArray(R.array.food_items)
        val ad = AlertDialog.Builder(this)
                .setCancelable(false)
                .setTitle(R.string.food_dialog_title)
                .setItems(choices) { _, which ->
                    val food = choices[which]
                    setUserFavoriteFood(food)
                }.create()
        ad.show()
    }

    /**
     * Get the user's favorite food from shared preferences.
     * @return favorite food, as a string.
     */
    private fun getUserFavoriteFood(): String? {
        return PreferenceManager.getDefaultSharedPreferences(this)
                .getString(KEY_FAVORITE_FOOD, null)
    }

    /**
     * Set the user's favorite food as an app measurement user property and in shared preferences.
     * @param food the user's favorite food.
     */
    private fun setUserFavoriteFood(food: String) {
        Log.d(TAG, "setFavoriteFood: $food")
        mFavoriteFood = food

        PreferenceManager.getDefaultSharedPreferences(this).edit()
                .putString(KEY_FAVORITE_FOOD, food)
                .apply()

        // [START user_property]
        mFirebaseAnalytics.setUserProperty("favorite_food", mFavoriteFood)
        // [END user_property]
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val i = item.itemId
        if (i == R.id.menu_share) {
            val name = getCurrentImageTitle()
            val text = "I'd love you to hear about $name"

            val sendIntent = Intent()
            sendIntent.action = Intent.ACTION_SEND
            sendIntent.putExtra(Intent.EXTRA_TEXT, text)
            sendIntent.type = "text/plain"
            startActivity(sendIntent)

            // [START custom_event]
            val params = Bundle()
            params.putString("image_name", name)
            params.putString("full_text", text)
            mFirebaseAnalytics.logEvent("share_image", params)
            // [END custom_event]
        }
        return false
    }

    /**
     * Return the title of the currently displayed image.
     *
     * @return title of image
     */
    private fun getCurrentImageTitle(): String {
        val position = pager.currentItem
        val (_, title) = IMAGE_INFOS[position]
        return getString(title)
    }

    /**
     * Return the id of the currently displayed image.
     *
     * @return id of image
     */
    private fun getCurrentImageId(): String {
        val position = pager.currentItem
        val (_, _, id) = IMAGE_INFOS[position]
        return getString(id)
    }

    /**
     * Record a screen view for the visible [ImageFragment] displayed
     * inside [FragmentPagerAdapter].
     */
    private fun recordImageView() {
        val id = getCurrentImageId()
        val name = getCurrentImageTitle()

        // [START image_view_event]
        val bundle = Bundle()
        bundle.putString(FirebaseAnalytics.Param.ITEM_ID, id)
        bundle.putString(FirebaseAnalytics.Param.ITEM_NAME, name)
        bundle.putString(FirebaseAnalytics.Param.CONTENT_TYPE, "image")
        mFirebaseAnalytics.logEvent(FirebaseAnalytics.Event.SELECT_CONTENT, bundle)
        // [END image_view_event]
    }

    /**
     * This sample has a single Activity, so we need to manually record "screen views" as
     * we change fragments.
     */
    private fun recordScreenView() {
        // This string must be <= 36 characters long in order for setCurrentScreen to succeed.
        val screenName = getCurrentImageId() + "-" + getCurrentImageTitle()

        // [START set_current_screen]
        mFirebaseAnalytics.setCurrentScreen(this, screenName, null /* class override */)
        // [END set_current_screen]
    }

    /**
     * A [FragmentPagerAdapter] that returns a fragment corresponding to
     * one of the sections/tabs/pages.
     */
    inner class ImagePagerAdapter(fm: FragmentManager, private val infos: Array<ImageInfo>) : FragmentPagerAdapter(fm) {

        override fun getItem(position: Int) = ImageFragment.newInstance(infos[position].image)

        override fun getCount() = infos.size

        override fun getPageTitle(position: Int): CharSequence? {
            if (position < 0 || position >= infos.size) {
                return null
            }
            val l = Locale.getDefault()
            val (_, title) = infos[position]
            return getString(title).toUpperCase(l)
        }
    }

}
