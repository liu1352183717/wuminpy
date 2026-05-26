package com.wumin.wuminpy.git

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.wumin.git.GitManager
import com.wumin.wuminpy.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class GitConfigActivity : AppCompatActivity() {

    private lateinit var etUserName: TextInputEditText
    private lateinit var etUserEmail: TextInputEditText
    private lateinit var etRepoPath: TextInputEditText
    private lateinit var btnSave: Button
    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_git_config)

        etUserName = findViewById(R.id.etUserName)
        etUserEmail = findViewById(R.id.etUserEmail)
        etRepoPath = findViewById(R.id.etRepoPath)
        btnSave = findViewById(R.id.btnSave)


        prefs = getSharedPreferences("git_config", Context.MODE_PRIVATE)

        // 加载已保存的全局配置
        etUserName.setText(prefs.getString("user.name", ""))
        etUserEmail.setText(prefs.getString("user.email", ""))


        btnSave.setOnClickListener {
            val name = etUserName.text.toString().trim()
            val email = etUserEmail.text.toString().trim()
            if (name.isEmpty() || email.isEmpty()) {
                Toast.makeText(this, "用户名和邮箱不能为空", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 保存到全局 SharedPreferences
            prefs.edit().apply {
                putString("user.name", name)
                putString("user.email", email)
                apply()
            }

            // 处理仓库路径（如果有）
            val repoPath = etRepoPath.text.toString().trim()
            if (repoPath.isNotEmpty()) {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val gitManager = GitManager(repoPath)
                        val initResult = gitManager.initOrOpen()
                        if (initResult.isSuccess) {
                            gitManager.setUserInfo(name, email)
                            withContext(Dispatchers.Main) {
                                Toast.makeText(
                                    this@GitConfigActivity,
                                    "配置已保存并应用到仓库: $repoPath",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(
                                    this@GitConfigActivity,
                                    "仓库路径无效或无法打开: ${initResult.exceptionOrNull()?.message}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                        gitManager.close()
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                this@GitConfigActivity,
                                "应用到仓库失败: ${e.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            } else {
                Toast.makeText(this, "全局配置已保存（未写入任何仓库）", Toast.LENGTH_SHORT).show()
            }
            finish()
        }
    }

}
