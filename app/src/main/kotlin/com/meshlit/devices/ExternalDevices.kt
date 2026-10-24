package com.meshlit.devices

import android.app.PendingIntent
import android.content.*
import android.content.pm.PackageManager
import android.hardware.usb.*
import android.os.storage.StorageManager
import kotlinx.serialization.Serializable

@Serializable data class UsbDeviceStatus(val path:String,val vendorId:Int,val productId:Int,val classes:List<Int>,val endpoints:Int,val permission:Boolean,val kind:String,val executionSupport:String)
@Serializable data class StorageVolumeStatus(val description:String,val uuid:String?,val removable:Boolean,val state:String)
@Serializable data class ExternalDeviceStatus(val usbHostSupported:Boolean,val devices:List<UsbDeviceStatus>,val volumes:List<StorageVolumeStatus>,val grantedTrees:List<String>)
class ExternalDevices(private val context:Context) {
    fun status():ExternalDeviceStatus {
        val usb=context.getSystemService(UsbManager::class.java)
        val devices=usb.deviceList.values.take(64).map{device ->
            val interfaces=(0 until device.interfaceCount).map{device.getInterface(it)}
            val classes=(listOf(device.deviceClass)+interfaces.map{it.interfaceClass}).distinct()
            val kind=when {
                UsbConstants.USB_CLASS_MASS_STORAGE in classes->"Storage · use Android's folder picker"
                UsbConstants.USB_CLASS_HID in classes->"Keyboard / input · Android driver"
                UsbConstants.USB_CLASS_AUDIO in classes->"Audio · Android routing"
                UsbConstants.USB_CLASS_VIDEO in classes->"Camera · needs a compatible camera adapter"
                UsbConstants.USB_CLASS_COMM in classes || UsbConstants.USB_CLASS_CDC_DATA in classes->"Serial / network · needs a compatible driver"
                UsbConstants.USB_CLASS_HUB in classes->"USB hub · Android host topology"
                else->"Vendor / other · driver capability unknown"
            }
            UsbDeviceStatus(device.deviceName,device.vendorId,device.productId,classes,interfaces.sumOf{it.endpointCount},usb.hasPermission(device),kind,
                "USB detection does not enable GPU/NPU inference. No external compute driver is installed.")
        }
        val storage=context.getSystemService(StorageManager::class.java)
        return ExternalDeviceStatus(context.packageManager.hasSystemFeature(PackageManager.FEATURE_USB_HOST),devices,
            storage.storageVolumes.map{StorageVolumeStatus(it.getDescription(context),it.uuid,it.isRemovable,it.state)},
            context.contentResolver.persistedUriPermissions.filter{it.isReadPermission}.map{it.uri.toString()})
    }
    fun requestPermission(path:String) {
        val usb=context.getSystemService(UsbManager::class.java)
        val device=usb.deviceList[path] ?: error("USB device was detached; refresh the list")
        val intent=Intent("${context.packageName}.USB_PERMISSION").setPackage(context.packageName)
        val pending=PendingIntent.getBroadcast(context,path.hashCode(),intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        usb.requestPermission(device,pending)
    }
}
