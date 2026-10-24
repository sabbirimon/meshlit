package com.meshlit.desktop

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.serialization.json.*
import java.awt.image.BufferedImage
import java.net.URI
import java.net.URLDecoder
import java.nio.file.*
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFilePermissions
import java.util.UUID
import javax.imageio.ImageIO

/** Studio uses the same Meshlit pairing URI as Android. A saved code never grants trust. */
data class DesktopPairingCode(val id:String,val link:String,val name:String,val endpoint:String,val nodeId:String,
                              val expiresAtMs:Long?,val savedAtMs:Long) {
    val temporary get()=expiresAtMs!=null
    val expired get()=expiresAtMs?.let { it<=System.currentTimeMillis() } ?: false
}

object DesktopPairingLinks {
    fun parse(raw:String):DesktopPairingCode {
        require(raw.length in 1..8192) { "Pairing link is empty or too large" }
        val uri=URI(raw.trim())
        require(uri.scheme.equals("meshlit",true) && uri.host.equals("pair",true)) { "Use a meshlit://pair QR" }
        require(uri.fragment==null && uri.userInfo==null) { "Invalid pairing link" }
        val pairs=uri.rawQuery.orEmpty().split('&').filter(String::isNotBlank).map { piece ->
            val parts=piece.split('=',limit=2);require(parts.size==2) { "Invalid pairing field" }
            URLDecoder.decode(parts[0],"UTF-8") to URLDecoder.decode(parts[1],"UTF-8")
        }
        require(pairs.map { it.first }.distinct().size==pairs.size) { "Duplicate pairing fields" }
        val fields=pairs.toMap()
        val endpoint=fields["url"].orEmpty()
        val target=URI(endpoint)
        require(target.scheme?.lowercase() in setOf("http","https") && !target.host.isNullOrBlank()) { "Invalid device endpoint" }
        require(target.userInfo==null && target.fragment==null && target.rawQuery==null &&
            (target.port==-1 || target.port in 1..65535)) { "Unsafe device endpoint" }
        val id=fields["node"].orEmpty()
        require(id.matches(Regex("(?:[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}|[0-9a-fA-F]{64})"))) { "Invalid device ID" }
        val expiry=fields["exp"]?.toLongOrNull()
        require(fields["exp"]==null || expiry!=null) { "Invalid QR expiry" }
        require(expiry==null || expiry>System.currentTimeMillis()) { "This temporary QR has expired" }
        require(fields["key"].orEmpty().length<=1024) { "Identity key too large" }
        return DesktopPairingCode(UUID.randomUUID().toString(),raw.trim(),fields["name"].orEmpty().take(128),endpoint,id,expiry,System.currentTimeMillis())
    }

    fun decodeImage(path:Path):String {
        require(Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS) && Files.size(path)<=12_000_000) { "Choose a PNG or JPEG under 12 MB" }
        val image=ImageIO.createImageInputStream(path.toFile()).use { input ->
            require(input!=null) { "Image cannot be opened" }
            val readers=ImageIO.getImageReaders(input)
            require(readers.hasNext()) { "Image cannot be decoded" }
            val reader=readers.next()
            try {
                reader.input=input
                require(reader.getWidth(0) in 1..4096 && reader.getHeight(0) in 1..4096) { "QR image is too large" }
                reader.read(0)
            } finally { reader.dispose() }
        }
        val pixels=IntArray(image.width*image.height)
        image.getRGB(0,0,image.width,image.height,pixels,0,image.width)
        val binary=BinaryBitmap(HybridBinarizer(RGBLuminanceSource(image.width,image.height,pixels)))
        return MultiFormatReader().decode(binary,mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),DecodeHintType.TRY_HARDER to true)).text
    }

    fun exportPng(link:String,path:Path) {
        require(link.length in 1..8192)
        val matrix=QRCodeWriter().encode(link,BarcodeFormat.QR_CODE,512,512)
        val image=BufferedImage(512,512,BufferedImage.TYPE_INT_RGB)
        for(y in 0 until 512) for(x in 0 until 512) image.setRGB(x,y,if(matrix[x,y]) 0x000000 else 0xffffff)
        require(ImageIO.write(image,"png",path.toFile())) { "PNG encoder unavailable" }
    }
}

class DesktopPairingCodeStore(private val directory:Path=Path.of(System.getProperty("meshlit.desktop.dataDir",
    Path.of(System.getProperty("user.home"),".meshlit","desktop").toString()))) {
    private val file get()=directory.resolve("pairing-codes.json")
    fun load():List<DesktopPairingCode> {
        if(!Files.exists(file,LinkOption.NOFOLLOW_LINKS)) return emptyList()
        require(!Files.isSymbolicLink(directory) && Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS) && Files.size(file)<=300_000)
        val array=Json.parseToJsonElement(Files.readString(file)).jsonArray
        require(array.size<=32)
        return array.map { entry -> val o=entry.jsonObject;DesktopPairingCode(
            o.getValue("id").jsonPrimitive.content,o.getValue("link").jsonPrimitive.content,
            o.getValue("name").jsonPrimitive.content,o.getValue("endpoint").jsonPrimitive.content,
            o.getValue("nodeId").jsonPrimitive.content,o["expiresAtMs"]?.jsonPrimitive?.longOrNull,
            o.getValue("savedAtMs").jsonPrimitive.long,
        ) }
    }
    fun save(entries:List<DesktopPairingCode>) {
        require(entries.size<=32 && entries.map { it.id }.distinct().size==entries.size)
        require(!Files.isSymbolicLink(directory) && !Files.isSymbolicLink(file))
        Files.createDirectories(directory)
        if(Files.getFileAttributeView(directory,PosixFileAttributeView::class.java)!=null)
            Files.setPosixFilePermissions(directory,PosixFilePermissions.fromString("rwx------"))
        val encoded=buildJsonArray { entries.forEach { e -> add(buildJsonObject {
            put("id",e.id);put("link",e.link);put("name",e.name);put("endpoint",e.endpoint)
            put("nodeId",e.nodeId);e.expiresAtMs?.let { put("expiresAtMs",it) };put("savedAtMs",e.savedAtMs)
        }) } }.toString()
        require(encoded.length<=300_000)
        val temp=Files.createTempFile(directory,"pairing-", ".tmp")
        try {
            if(Files.getFileAttributeView(temp,PosixFileAttributeView::class.java)!=null)
                Files.setPosixFilePermissions(temp,PosixFilePermissions.fromString("rw-------"))
            Files.writeString(temp,encoded)
            java.nio.channels.FileChannel.open(temp,StandardOpenOption.WRITE).use { it.force(true) }
            Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING)
        } finally { Files.deleteIfExists(temp) }
    }
}
