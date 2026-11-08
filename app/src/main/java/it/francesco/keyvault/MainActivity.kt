package it.francesco.keyvault

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import it.francesco.keyvault.ui.KeyVaultApp

private val Navy = Color(0xFF101827)
private val Surface = Color(0xFF182235)
private val Mint = Color(0xFF55E0B5)
private val TextPrimary = Color(0xFFF4F7FB)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Mint,
                    onPrimary = Navy,
                    background = Navy,
                    surface = Surface,
                    onSurface = TextPrimary,
                    onBackground = TextPrimary,
                    secondary = Color(0xFF9AAAC1),
                    error = Color(0xFFFF6B78)
                )
            ) { KeyVaultApp(this) }
        }
    }
}
