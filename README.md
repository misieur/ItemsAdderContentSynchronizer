This is a Paper plugin made for 1.20.1+, it allows you to automatically synchronize your ItemsAdder contents folder with a public or private GitHub repository that you own.
It will automatically create a webhook on your repository using the given configuration to know exactly when a new release is published and update your server's contents folder and resource pack — it is made to work on multiple server at the time.

---

Showcase

https://github.com/user-attachments/assets/d5f374a2-7643-469e-ae35-65be4f451c11

---

`config.yml`
```YAML
webhook_url: https://example.com:8080/github-release # a public IP to update your server as soon as a new release is published
webhook_port: 8080 # the available port you want to use
use_webhook: true # If false, the plugin will not use the webhook to update the content folder. You will have to restart the server to update it.
github_token: # The GitHub that will be used to manage webhooks and download the releases
repo_owner: owner
repo_name: repo # it can be private
exclude_folders: # folders that will not be deleted or updated
  - _iainternal # You can also put '.idea' '.vscode' etc. if needed
```

---

How to setup

https://github.com/user-attachments/assets/41c46655-9997-4bdc-98cb-a425320e9891

---

<p align="center">
  <a href="https://bstats.org/plugin/bukkit/ItemsAdderContentSynchronizer/26744" target="_blank">
    <img src="https://bstats.org/signatures/bukkit/ItemsAdderContentSynchronizer.svg" alt="Metrics" width="800"/>
  </a>
</p>
