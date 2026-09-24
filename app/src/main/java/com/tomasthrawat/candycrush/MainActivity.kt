package com.tomasthrawat.candycrush

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.tomasthrawat.candycrush.databinding.ActivityMainBinding
import com.tomasthrawat.candycrush.viewmodel.GameViewModel

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val gameViewModel by viewModels<GameViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = getColor(R.color.game_background)
        window.navigationBarColor = getColor(R.color.game_background_bottom)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.gameView.attachViewModel(gameViewModel)
        binding.gameView.startGame()
    }
}
