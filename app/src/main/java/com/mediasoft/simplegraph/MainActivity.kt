package com.mediasoft.simplegraph

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.*

class MainActivity : AppCompatActivity() {

    private val points1 = listOf(
        GraphView.Point.create(
            date = Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1) }.time,
            price = 5_000
        ),
        GraphView.Point.create(
            date = Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 2) }.time,
            price = 6_500
        ),
        GraphView.Point.create(
            date = Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 3) }.time,
            price = 7_000
        ),
        GraphView.Point.create(
            date = Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 4) }.time,
            price = 4_000
        ),
        GraphView.Point.create(
            date = Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 12) }.time,
            price = 8_200
        ),
    )

    private val points2 = listOf(
        GraphView.Point.create(
            date = 100,
            price = 5_000
        ),
        GraphView.Point.create(
            date = 150,
            price = 6_500
        ),
        GraphView.Point.create(
            date = 200,
            price = 7_000
        ),
        GraphView.Point.create(
            date = 250,
            price = 4_000
        ),
        GraphView.Point.create(
            date = 300,
            price = 8_200
        ),
        GraphView.Point.create(
            date = 400,
            price = 5_500
        ),
        GraphView.Point.create(
            date = 600,
            price = 3_500
        ),
    )

    private val points3 = listOf(
        GraphView.Point.create(
            Calendar.getInstance()
                .apply { set(Calendar.DAY_OF_YEAR, 1) }.time,
            2_100
        ),
        GraphView.Point.create(
            Calendar.getInstance()
                .apply { set(Calendar.DAY_OF_YEAR, 2) }.time,
            2_500
        ),
        GraphView.Point.create(
            Calendar.getInstance()
                .apply { set(Calendar.DAY_OF_YEAR, 3) }.time,
            2_400
        ),
        GraphView.Point.create(
            Calendar.getInstance()
                .apply { set(Calendar.DAY_OF_YEAR, 4) }.time,
            2_000
        ),
        GraphView.Point.create(
            Calendar.getInstance()
                .apply { set(Calendar.DAY_OF_YEAR, 5) }.time,
            2_500
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        findViewById<GraphView>(R.id.graph)?.apply {
            setPoints(points3)
            setListener(object : GraphView.Listener {
                override fun onPointSelected(point: GraphView.Point): Boolean {
                    return true
                }

                override fun onPointReselected(point: GraphView.Point): Boolean {
                    return true
                }

                override fun provideToolTipText(point: GraphView.Point): String? {
                    return DecimalFormat().apply {
                        this.groupingSize = 3
                        this.decimalFormatSymbols = DecimalFormatSymbols.getInstance(Locale.getDefault())
                    }.format(point.y)
                }
            })
//            selectPoint(points3.last())
        }
    }

}