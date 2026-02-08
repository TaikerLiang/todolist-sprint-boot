# Wiki Setup Instructions

This directory contains wiki documentation for the Todo List API project.

## 📁 Local Wiki Structure

The `wiki/` folder in the repository serves as both:
1. **Local documentation** - Available offline in the repository
2. **GitHub Wiki source** - Can be synced to GitHub Wiki

## 🔗 Connecting to GitHub Wiki

### Option 1: Manual Sync (Recommended for now)

1. **Enable GitHub Wiki** for your repository:
   ```bash
   # Go to: https://github.com/YOUR_USERNAME/todolist/settings
   # Enable "Wikis" in Features section
   ```

2. **Clone the Wiki repository**:
   ```bash
   git clone https://github.com/YOUR_USERNAME/todolist.wiki.git
   ```

3. **Copy wiki files**:
   ```bash
   cd todolist.wiki
   cp ../wiki/*.md .
   git add .
   git commit -m "Add wiki documentation"
   git push origin master
   ```

### Option 2: Automated Sync with GitHub Actions

Create `.github/workflows/sync-wiki.yml`:

```yaml
name: Sync Wiki

on:
  push:
    branches: [main]
    paths:
      - 'wiki/**'

jobs:
  sync:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout main repo
        uses: actions/checkout@v3

      - name: Checkout wiki repo
        uses: actions/checkout@v3
        with:
          repository: ${{ github.repository }}.wiki
          path: wiki-repo

      - name: Sync files
        run: |
          cp wiki/*.md wiki-repo/
          cd wiki-repo
          git config user.name "GitHub Action"
          git config user.email "action@github.com"
          git add .
          git commit -m "Sync wiki from main repo" || exit 0
          git push
```

### Option 3: Git Submodule (Advanced)

Make the wiki a submodule:

```bash
# In your main repo
git submodule add https://github.com/YOUR_USERNAME/todolist.wiki.git wiki-github
```

## 📝 Creating New Wiki Pages

1. Create a new `.md` file in the `wiki/` directory
2. Use kebab-case for filenames: `My-Page-Title.md`
3. Link from `Home.md`:
   ```markdown
   - [My Page Title](My-Page-Title.md)
   ```

## 🎨 Wiki Naming Conventions

- **Home.md** - Main wiki landing page
- **Kebab-Case-Titles.md** - All other pages
- Use descriptive names that match the page content

## 📚 Current Wiki Pages

- `Home.md` - Wiki home page with table of contents
- `Why-Multiple-Refresh-Tokens.md` - JWT architecture decision
- `JWT-Authentication-Overview.md` - JWT authentication guide
- `README.md` - This file

## 🔄 Keeping Wiki Updated

1. **Update local wiki files** in the `wiki/` directory
2. **Commit to main repository**:
   ```bash
   git add wiki/
   git commit -m "docs: update wiki documentation"
   git push
   ```
3. **Sync to GitHub Wiki** (manual or automated)

## 🎯 Best Practices

1. ✅ Keep wiki files in version control (this `wiki/` folder)
2. ✅ Use relative links between wiki pages
3. ✅ Include "Last Updated" date at bottom of each page
4. ✅ Link to related documentation
5. ✅ Use code examples and diagrams
6. ✅ Keep language clear and concise

## 🔗 External Links

- **GitHub Wiki**: https://github.com/YOUR_USERNAME/todolist/wiki
- **Main Repo**: https://github.com/YOUR_USERNAME/todolist
- **Issues**: https://github.com/YOUR_USERNAME/todolist/issues

---

**Last Updated**: 2026-02-08
