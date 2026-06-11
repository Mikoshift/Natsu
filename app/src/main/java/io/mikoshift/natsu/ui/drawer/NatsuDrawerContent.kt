package io.mikoshift.natsu.ui.drawer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.LibraryBooks
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.mikoshift.natsu.R
import io.mikoshift.natsu.domain.model.UserProfileStub
import io.mikoshift.natsu.ui.navigation.Routes

@Composable
fun NatsuDrawerHeader(
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.primaryContainer,
                        ),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = (-32).dp)
                .padding(horizontal = 16.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .align(Alignment.CenterStart),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = UserProfileStub.DISPLAY_NAME.first().toString(),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = UserProfileStub.DISPLAY_NAME,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
fun NatsuDrawerContent(
    selectedRoute: String?,
    onNavigateToLibrary: () -> Unit,
    onNavigateToDictionaries: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .fillMaxWidth(),
    ) {
        NatsuDrawerHeader()
        NavigationDrawerItem(
            label = { Text(stringResource(R.string.drawer_library)) },
            selected = selectedRoute == Routes.LIBRARY,
            onClick = onNavigateToLibrary,
            icon = {
                Icon(
                    Icons.Outlined.LibraryBooks,
                    contentDescription = null,
                )
            },
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        NavigationDrawerItem(
            label = { Text(stringResource(R.string.drawer_dictionaries)) },
            selected = selectedRoute == Routes.DICTIONARIES,
            onClick = onNavigateToDictionaries,
            icon = {
                Icon(
                    Icons.AutoMirrored.Outlined.MenuBook,
                    contentDescription = null,
                )
            },
            modifier = Modifier.padding(horizontal = 12.dp),
        )
    }
}
