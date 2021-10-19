package org.starx_software_lab.v2native.widget

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import org.starx_software_lab.v2native.R

class CustomizedCard(context: Context, attrs: AttributeSet?) : LinearLayout(context, attrs) {
    val layout: View =
        LayoutInflater.from(context).inflate(R.layout.cardview_with_icon_and_caption, this)
    val card: CardView = layout.findViewById(R.id.widget_Card)
    val icon: ImageView = layout.findViewById(R.id.widget_CardIcon)
    val caption: TextView = layout.findViewById(R.id.widget_CardCaption)
    val body: TextView = layout.findViewById(R.id.widget_CardBody)

    override fun setBackgroundColor(color: Int) {
        this.card.setCardBackgroundColor(color)
    }

    override fun setOnClickListener(l: OnClickListener?) {
        this.card.setOnClickListener(l)
    }

    override fun setOnLongClickListener(l: OnLongClickListener?) {
        this.card.setOnLongClickListener(l)
    }

    fun setIcon(s: Drawable) {
        this.icon.background = s
    }

    //  default color => black
    fun setCaption(s: String, color: Int?) {
        this.caption.text = s
        if (color != null) {
            this.caption.setTextColor(color)
        }
    }

    fun setBody(s: String, color: Int?) {
        this.body.text = s
        if (color != null) {
            this.body.setTextColor(color)
        }
    }
}