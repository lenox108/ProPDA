package forpdateam.ru.forpda.ui.fragments.news.details

import org.json.JSONObject

/**
 * Pure builders for the JavaScript snippets evaluated inside the article WebView (poll bind,
 * comments-section bind, inline-comments state). Extracted from [ArticleContentFragment]: each
 * snippet is a pure function of its arguments, so the JS templates live in one place and the
 * argument quoting can be unit-tested without a WebView.
 */
object NewsWebViewScripts {

    fun pollBind(): String =
            """(function(){
              try{
                if(typeof transformPoll==='function'){transformPoll();}
                if(typeof bindPollExternalBrowserButtons==='function'){bindPollExternalBrowserButtons();}
                var root=document.querySelector('#news .content div.news-poll-normalized,#news .content div[id*="poll-ajax-frame"],#news .content .news-poll');
                var form=root?root.querySelector('form'):null;
                var opts=form?form.querySelectorAll('input[name="answer[]"],input[name="answer"],input[name^="answer["]'):[];
                var pollId='';
                if(form){
                  var m=/poll_id=(\d+)/.exec(form.getAttribute('action')||'');
                  if(m){pollId=m[1];}
                  if(!pollId){
                    var input=form.querySelector('input[name="poll_id"],input[name="poll"]');
                    pollId=input&&input.value?input.value:'';
                  }
                }
                var submit=form?form.querySelector('button[type="submit"],input[type="submit"],button.btn,.vote,.btn'):null;
                var results=root?root.querySelectorAll('.poll-list .slider,.poll-list .range,.poll-list .value'):[];
                return JSON.stringify({
                  pollRootFound:!!root,
                  pollId:pollId||'',
                  optionsCount:opts?opts.length:0,
                  canVote:!!(form&&submit&&!submit.disabled&&opts&&opts.length>0),
                  hasToken:root?!!root.getAttribute('data-news-poll-token'):false,
                  renderedPollBlock:!!root,
                  readOnlyResults:results&&results.length>0,
                  boundSubmit:!!(submit&&submit.dataset&&submit.dataset.newsPollBound==='true')
                });
              }catch(e){
                return JSON.stringify({pollRootFound:false,error:String(e&&e.message?e.message:e)});
              }
            })();"""

    fun bindCommentsSection(collapsed: Boolean, domState: String, generation: Int): String =
            """(function(){
                try{
                  var collapsed=${collapsed};
                  var domState=${JSONObject.quote(domState)};
                  var generation=${generation};
                  if (typeof bindCommentsSection === 'function') {
                    bindCommentsSection(collapsed, domState);
                  } else if (typeof bindNewsInlineCommentsLoad === 'function') {
                    bindNewsInlineCommentsLoad();
                  }
                  if (typeof newsInlineCommentsPruneDuplicates === 'function') {
                    newsInlineCommentsPruneDuplicates();
                  }
                  var sections = document.querySelectorAll('#news-comments-section');
                  var root = null;
                  if (typeof newsInlineCommentsRoot === 'function') {
                    root = newsInlineCommentsRoot();
                  } else if (sections.length) {
                    root = sections[0];
                  }
                  if (root) {
                    root.setAttribute('data-fpda-webview-gen', String(generation));
                  }
                  var toggle = root ? root.querySelector('#news-comments-toggle') : null;
                  var doc = document.documentElement;
                  return JSON.stringify({
                    hasRoot: !!root,
                    hasToggle: !!toggle,
                    sectionCount: sections ? sections.length : 0,
                    delegation: !!(doc && doc.getAttribute('data-fpda-comments-delegation') === '1'),
                    commentsJsReady: typeof bindCommentsSection === 'function' &&
                      typeof newsInlineCommentsInjectHtml === 'function' &&
                      typeof newsInlineCommentsSetState === 'function'
                  });
                }catch(e){
                  return JSON.stringify({hasRoot:false,hasToggle:false,sectionCount:0,delegation:false,commentsJsReady:false});
                }
            })();"""

    fun inlineCommentsState(
            state: String,
            message: String,
            html: String?,
            scrollToCommentId: Int = 0,
            canLoadMore: Boolean = false,
            totalCount: Int = 0,
            renderedCount: Int = 0,
    ): String =
            """(function(){
                if (typeof newsInlineCommentsSetState !== 'function') return;
                newsInlineCommentsSetState(""" +
                    JSONObject.quote(state) +
                    "," +
                    JSONObject.quote(message) +
                    "," +
                    (html?.let { JSONObject.quote(it) } ?: "null") +
                    "," +
                    scrollToCommentId +
                    """);
                if (""" + JSONObject.quote(state) + """ === "loaded" &&
                        typeof newsInlineCommentsUpdateLoadMore === "function") {
                    newsInlineCommentsUpdateLoadMore($canLoadMore, $totalCount, $renderedCount);
                }
            })();"""
}
