package ru.zhivo.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Light=lightColorScheme(primary=Color(0xFF214E3A),onPrimary=Color.White,primaryContainer=Color(0xFFDCEAD8),onPrimaryContainer=Color(0xFF183D2B),secondary=Color(0xFF806027),secondaryContainer=Color(0xFFF1E5C9),background=Color(0xFFF6F3EC),surface=Color(0xFFFFFDF7),surfaceVariant=Color(0xFFE9EDE3),onBackground=Color(0xFF1C2D24),onSurface=Color(0xFF1C2D24),outline=Color(0xFF748276))
private val Dark=darkColorScheme(primary=Color(0xFFADDBB2),onPrimary=Color(0xFF173824),primaryContainer=Color(0xFF284A35),onPrimaryContainer=Color(0xFFD6EDCF),secondary=Color(0xFFE8C684),secondaryContainer=Color(0xFF514123),background=Color(0xFF101C16),surface=Color(0xFF1B2B22),surfaceVariant=Color(0xFF2A3A2F),onBackground=Color(0xFFE5EBDF),onSurface=Color(0xFFE5EBDF),outline=Color(0xFF8E9C8E))
@Composable fun ZhivoTheme(theme:String,content: @Composable () -> Unit){MaterialTheme(colorScheme=if(theme=="dark"||(theme=="system"&&isSystemInDarkTheme()))Dark else Light,typography=Typography(),content=content)}
@Composable fun Page(title:String,subtitle:String="",back:(()->Unit)?=null,content:@Composable ColumnScope.()->Unit){
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=22.dp).padding(top=18.dp,bottom=30.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
        if(back!=null)TextButton(onClick=back,contentPadding=PaddingValues(0.dp)){Text("← Назад")}
        Text(title,fontSize=30.sp,fontWeight=FontWeight.SemiBold,fontFamily=FontFamily.Serif,lineHeight=36.sp)
        if(subtitle.isNotBlank())Text(subtitle,style=MaterialTheme.typography.bodyLarge,color=MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}
@Composable fun Panel(modifier:Modifier=Modifier,tonal:Boolean=false,content:@Composable ColumnScope.()->Unit){
    Surface(modifier.fillMaxWidth(),shape=RoundedCornerShape(24.dp),color=if(tonal)MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,tonalElevation=if(tonal)0.dp else 1.dp){Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp),content=content)}
}
@Composable fun Label(text:String){Text(text,style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Bold)}
@Composable fun Head(text:String,modifier:Modifier=Modifier){Text(text,modifier=modifier,fontSize=21.sp,fontWeight=FontWeight.SemiBold,lineHeight=28.sp)}
@Composable fun Small(text:String){Text(text,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)}
@Composable fun Action(text:String,enabled:Boolean=true,onClick:()->Unit){Button(onClick=onClick,enabled=enabled,modifier=Modifier.fillMaxWidth().heightIn(min=54.dp),shape=RoundedCornerShape(16.dp),contentPadding=PaddingValues(16.dp)){Text(text,style=MaterialTheme.typography.titleMedium)}}
@Composable fun Secondary(text:String,enabled:Boolean=true,onClick:()->Unit){OutlinedButton(onClick=onClick,enabled=enabled,modifier=Modifier.fillMaxWidth().heightIn(min=50.dp),shape=RoundedCornerShape(16.dp),contentPadding=PaddingValues(14.dp)){Text(text)}}
@Composable fun Choice(text:String,selected:Boolean,onClick:()->Unit,enabled:Boolean=true){
    OutlinedButton(onClick=onClick,enabled=enabled,shape=RoundedCornerShape(16.dp),modifier=Modifier.fillMaxWidth().heightIn(min=54.dp),colors=ButtonDefaults.outlinedButtonColors(containerColor=if(selected)MaterialTheme.colorScheme.primaryContainer else Color.Transparent,contentColor=MaterialTheme.colorScheme.onSurface),contentPadding=PaddingValues(16.dp)){
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
            Box(Modifier.size(20.dp).background(if(selected)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,CircleShape),contentAlignment=Alignment.Center){if(selected)Text("✓",color=MaterialTheme.colorScheme.onPrimary,fontSize=13.sp)}
            Text(text,Modifier.weight(1f),style=MaterialTheme.typography.bodyLarge)
        }
    }
}
@Composable fun EmptyState(title:String,text:String){Panel{Head(title);Small(text)}}
@Composable fun Stat(value:String,label:String,modifier:Modifier=Modifier){Column(modifier,verticalArrangement=Arrangement.spacedBy(4.dp)){Text(value,fontSize=26.sp,fontWeight=FontWeight.SemiBold,color=MaterialTheme.colorScheme.primary);Small(label)}}
@Composable fun Line(title:String,sub:String,tag:String="",onClick:()->Unit){Surface(Modifier.fillMaxWidth().clickable(onClick=onClick),shape=RoundedCornerShape(20.dp),color=MaterialTheme.colorScheme.surface){Row(Modifier.padding(18.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)){Text(title,style=MaterialTheme.typography.titleMedium);Small(sub)};if(tag.isNotEmpty())Label(tag)}}}
