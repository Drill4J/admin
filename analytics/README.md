# Google Analytics module

This module is used to send anonymous data to google universal analytics.

Messages are sent by HTTP post request to https://www.google-analytics.com/collect.
- The message type used to send is an `event`.
- Body example: `v=1&de=UTF-8&tid=UA-214931987-2&t=event&ea=test1&ec=Test&cid=f99d9241-54ce-4466-9cdb-dde15c40eb69`

cid - Client ID generated once to identify the application, it could be found on file system in /user/home/.drill/drill.properties

If you want to disable analytics, then set system property `analytic.disable = true` or send PATCH request `/api/analytic/toggle`

For more details see [Google Analytics API](https://developers.google.com/analytics/devguides/collection/protocol/v1/parameters)
