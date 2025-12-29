package com.e621.client.ui.post

import android.content.Context
import android.view.LayoutInflater
import android.widget.RadioButton
import android.widget.RadioGroup
import com.e621.client.R
import com.google.android.material.bottomsheet.BottomSheetDialog

/**
 * YouTube-style speed selector bottom sheet
 */
class SpeedSelectorDialog(
    context: Context,
    private val currentSpeed: Float,
    private val onSpeedSelected: (Float) -> Unit
) {
    private val dialog = BottomSheetDialog(context, R.style.BottomSheetDialogTheme)
    
    private val speedMap = mapOf(
        R.id.speed025 to 0.25f,
        R.id.speed05 to 0.5f,
        R.id.speed075 to 0.75f,
        R.id.speed1 to 1.0f,
        R.id.speed125 to 1.25f,
        R.id.speed15 to 1.5f,
        R.id.speed175 to 1.75f,
        R.id.speed2 to 2.0f
    )
    
    init {
        val view = LayoutInflater.from(context).inflate(R.layout.bottom_sheet_speed, null)
        dialog.setContentView(view)
        
        // Make background transparent to show rounded corners
        dialog.window?.findViewById<android.view.View>(com.google.android.material.R.id.design_bottom_sheet)?.apply {
            setBackgroundResource(android.R.color.transparent)
        }
        
        val radioGroup = view.findViewById<RadioGroup>(R.id.speedRadioGroup)
        
        // Select current speed
        speedMap.entries.find { it.value == currentSpeed }?.let { entry ->
            view.findViewById<RadioButton>(entry.key)?.isChecked = true
        } ?: run {
            // Default to normal speed if not found
            view.findViewById<RadioButton>(R.id.speed1)?.isChecked = true
        }
        
        // Handle selection
        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            speedMap[checkedId]?.let { speed ->
                onSpeedSelected(speed)
                dialog.dismiss()
            }
        }
    }
    
    fun show() {
        dialog.show()
    }
}
