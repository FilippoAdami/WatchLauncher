package com.example.watchlauncher

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ContactsAdapter(private val context: Context, private val contacts: List<MainActivity.ContactInfo>) :
    RecyclerView.Adapter<ContactsAdapter.ContactViewHolder>() {

    private val VIEW_TYPE_ITEM = 0
    private val VIEW_TYPE_EMPTY = 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.contact_item, parent, false)
        return ContactViewHolder(view)
    }

    override fun onBindViewHolder(holder: ContactViewHolder, position: Int) {
        val contact = contacts[position % contacts.size]
        holder.contactName.text = contact.name
    }

    override fun getItemCount(): Int {
        return Int.MAX_VALUE // This creates the infinite scroll effect
    }

    inner class ContactViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val contactName: TextView = itemView.findViewById(R.id.contact_name)
    }
}
