package com.ahmedabdelmeged.firestore

import android.app.Activity
import android.arch.lifecycle.ViewModelProviders
import android.content.Intent
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.annotation.StringRes
import android.support.design.widget.Snackbar
import android.support.v7.app.AlertDialog
import android.support.v7.widget.LinearLayoutManager
import android.text.Html
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import com.ahmedabdelmeged.firestore.adapter.RestaurantAdapter
import com.ahmedabdelmeged.firestore.model.Restaurant
import com.ahmedabdelmeged.firestore.util.RatingUtil
import com.ahmedabdelmeged.firestore.util.RestaurantUtil
import com.ahmedabdelmeged.firestore.viewmodel.MainActivityViewModel
import com.firebase.ui.auth.AuthUI
import com.firebase.ui.auth.ErrorCodes
import com.firebase.ui.auth.IdpResponse
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import kotlinx.android.synthetic.main.activity_main.*

open class MainActivity : AppCompatActivity(), FilterDialogFragment.FilterListener, RestaurantAdapter.OnRestaurantSelectedListener {

    private val TAG = "MainActivity"

    private val RC_SIGN_IN = 9001

    private val LIMIT = 50

    private val mFirestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    private val mQuery: Query by lazy {
        mFirestore.collection("restaurants")
                .orderBy("avgRating", Query.Direction.DESCENDING)
                .limit(LIMIT.toLong())
    }

    private val mFilterDialog: FilterDialogFragment by lazy { FilterDialogFragment() }
    private var mAdapter: RestaurantAdapter? = null

    private lateinit var mViewModel: MainActivityViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)

        // View model
        mViewModel = ViewModelProviders.of(this).get(MainActivityViewModel::class.java)

        // Enable Firestore logging
        FirebaseFirestore.setLoggingEnabled(true)

        // RecyclerView
        mAdapter = object : RestaurantAdapter(mQuery, this@MainActivity) {
            override fun onDataChanged() {
                // Show/hide content if the query returns empty.
                if (itemCount == 0) {
                    recycler_restaurants.visibility = View.GONE
                    view_empty.visibility = View.VISIBLE
                } else {
                    recycler_restaurants.visibility = View.VISIBLE
                    view_empty.visibility = View.GONE
                }
            }

            override fun onError(e: FirebaseFirestoreException) {
                // Show a snackbar on errors
                Snackbar.make(findViewById(android.R.id.content),
                        "Error: check logs for info.", Snackbar.LENGTH_LONG).show()
            }
        }

        recycler_restaurants.layoutManager = LinearLayoutManager(this)
        recycler_restaurants.adapter = mAdapter

        button_clear_filter.setOnClickListener {
            mFilterDialog.resetFilters()

            onFilter(Filters.default())
        }

        filter_bar.setOnClickListener { mFilterDialog.show(supportFragmentManager, FilterDialogFragment.TAG) }
    }

    public override fun onStart() {
        super.onStart()
        // Start sign in if necessary
        if (shouldStartSignIn()) {
            startSignIn()
            return
        }

        // Apply filters
        onFilter(mViewModel.filters)

        // Start listening for Firestore updates
        if (mAdapter != null) {
            mAdapter!!.startListening()
        }
    }

    public override fun onStop() {
        super.onStop()
        if (mAdapter != null) {
            mAdapter!!.stopListening()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_add_items -> onAddItemsClicked()
            R.id.menu_sign_out -> {
                AuthUI.getInstance().signOut(this)
                startSignIn()
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_SIGN_IN) {
            val response = IdpResponse.fromResultIntent(data)
            mViewModel.isSigningIn = false

            if (resultCode != Activity.RESULT_OK) {
                when {
                    response == null -> // User pressed the back button.
                        finish()
                    response.error!!.errorCode == ErrorCodes.NO_NETWORK -> showSignInErrorDialog(R.string.message_no_network)
                    else -> showSignInErrorDialog(R.string.message_unknown)
                }
            }
        }
    }

    override fun onRestaurantSelected(restaurant: DocumentSnapshot) {
        // Go to the details page for the selected restaurant
        val intent = Intent(this, RestaurantDetailActivity::class.java)
        intent.putExtra(RestaurantDetailActivity.KEY_RESTAURANT_ID, restaurant.id)

        startActivity(intent)
        overridePendingTransition(R.anim.slide_in_from_right, R.anim.slide_out_to_left)
    }

    override fun onFilter(filters: Filters) {
        // Construct query basic query
        var query: Query = mFirestore.collection("restaurants")

        // Category (equality filter)
        if (filters.hasCategory()) {
            query = query.whereEqualTo(Restaurant.FIELD_CATEGORY, filters.category)
        }

        // City (equality filter)
        if (filters.hasCity()) {
            query = query.whereEqualTo(Restaurant.FIELD_CITY, filters.city)
        }

        // Price (equality filter)
        if (filters.hasPrice()) {
            query = query.whereEqualTo(Restaurant.FIELD_PRICE, filters.price)
        }

        // Sort by (orderBy with direction)
        if (filters.hasSortBy()) {
            query = query.orderBy(filters.sortBy!!, filters.sortDirection!!)
        }

        // Limit items
        query = query.limit(LIMIT.toLong())

        // Update the query
        mAdapter!!.setQuery(query)

        // Set header
        text_current_search.text = Html.fromHtml(filters.getSearchDescription(this))
        text_current_sort_by.text = filters.getOrderDescription(this)

        // Save filters
        mViewModel.filters = filters
    }

    private fun shouldStartSignIn(): Boolean {
        return !mViewModel.isSigningIn && FirebaseAuth.getInstance().currentUser == null
    }

    private fun startSignIn() {
        // Sign in with FirebaseUI
        val intent = AuthUI.getInstance().createSignInIntentBuilder()
                .setAvailableProviders(listOf(AuthUI.IdpConfig.Builder(AuthUI.EMAIL_PROVIDER).build()))
                .setIsSmartLockEnabled(false)
                .build()

        startActivityForResult(intent, RC_SIGN_IN)
        mViewModel.isSigningIn = true
    }

    private fun onAddItemsClicked() {
        // Add a bunch of random restaurants
        val batch = mFirestore.batch()
        for (i in 0..9) {
            val restRef = mFirestore.collection("restaurants").document()

            // Create random restaurant / ratings
            val randomRestaurant = RestaurantUtil.getRandom(this)
            val randomRatings = RatingUtil.getRandomList(randomRestaurant.numRatings)
            randomRestaurant.avgRating = RatingUtil.getAverageRating(randomRatings)

            // Add restaurant
            batch.set(restRef, randomRestaurant)

            // Add ratings to subcollection
            for (rating in randomRatings) {
                batch.set(restRef.collection("ratings").document(), rating)
            }
        }

        batch.commit().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Log.d(TAG, "Write batch succeeded.")
            } else {
                Log.w(TAG, "write batch failed.", task.exception)
            }
        }
    }

    private fun showSignInErrorDialog(@StringRes message: Int) {
        val dialog = AlertDialog.Builder(this)
                .setTitle(R.string.title_sign_in_error)
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton(R.string.option_retry) { _, _ -> startSignIn() }
                .setNegativeButton(R.string.option_exit) { _, _ -> finish() }.create()

        dialog.show()
    }

}
