package edu.artic.welcome

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.navigation.Navigation
import edu.artic.base.utils.disableShiftMode
import edu.artic.navigation.NavigationSelectListener
import edu.artic.ui.BaseActivity
import kotlinx.android.synthetic.main.activity_welcome.*

class WelcomeActivity : BaseActivity() {

    companion object {
        val EXTRA_QUIT: String = "EXTRA_QUIT"

        fun quitIntent(context: Context): Intent {
            val intent = Intent(context, WelcomeActivity::class.java)
            intent.putExtra(EXTRA_QUIT, true)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
            return intent
        }
    }

    override val layoutResId: Int
        get() = R.layout.activity_welcome

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent?.extras?.getBoolean(EXTRA_QUIT) == true) {
            finish()
            return
        }
        bottomNavigation.disableShiftMode(R.color.menu_color_list)
        bottomNavigation.selectedItemId = R.id.action_home
        bottomNavigation.setOnNavigationItemSelectedListener(NavigationSelectListener(this))
    }

    override fun onBackPressed() {
        if (!isTaskRoot && supportFragmentManager.backStackEntryCount == 0) {
            val navigationController = Navigation.findNavController(this, R.id.container)
            if (navigationController.currentDestination.id == R.id.welcomeFragment) {
                startActivity(quitIntent(this))
                finish()
                return
            }
        }
        super.onBackPressed()
    }
}