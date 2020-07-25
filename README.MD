# Uploads Service

Note: all paths are relative to the API base. (`https://api.japan-impact.ch/uploads/`)

## Hierarchy

Files are uploaded to containers. Containers are owned by apps. Container names are unique within apps, but not among them.

The container is named, and it defines a set of accepted Mime Types (+ a maximum file size for each file).

## How to upload?

You will typically first want to create/update a container to receive the files. Once you have it:

### 1. Create the upload request

From your backend, do a POST to `/files/:appId/:containerName`
 
```json
{
  "uploader": "<optional: the principal that will be able to upload the file>",
  "replacement": "one of: NoReplace, ReplaceOne, ReplaceAll",
  "replaceId": 0
}
```

Only `replacement` is mandatory: it defines whether or not you want to replace records.
 - `NoReplace` will simply insert the uploaded file
 - `ReplaceOne` will replace the file `replaceId` by the new uploaded file (it will actually delete the old file). 
 - `ReplaceAll` will delete all the files uploaded by the same target uploader before uploading the new file

Note: a principal can either be an App (or _ApiKey_), or a User. The principal is `principalType|id`. For example, `user|1` or `app|10`.

#### Scopes

If you are uploading to one of your containers (`appId` == to the app id in your auth token), you need the `uploads/requests/self/new` scope.

If you are uploading to the container of an other app, you need the `uploads/requests/:appId/new/:containerName` scope.

#### Response

If everything goes well, you'll receive the following response:

```json
{ "success": true, "data": "<the upload ticket>" }
```

If there is an error, you'll receive this (with a non 200 result code):

```json
{ "success": false, "error": "short_error_code", "errorMessage": "A nicer error message." }
```

The ticket you receive in the response is valid for a short period of time (< 24h). It is this ticket that you need to send to the user in order for
them to upload.

### 2. Upload the file

Simply do a `PUT` to `/files/:uploadTicket`.
The file should be directly in the body (no multipart/form-data).


#### Scopes

No scope is needed for this endpoint, but if you specified an `uploader` in the request, then the sender MUST have an auth token for the same principal.

#### Response

If everything goes well, you'll receive the following response:

```json
{ "success": true, "data": {
  "ticket": "<the ticket you sent>", "url": "the url at which the file can be accessed"
} }
```

If there is an error, you'll receive this (with a non 200 result code):

```json
{ "success": false, "error": "short_error_code", "errorMessage": "A nicer error message." }
```

The URL is provided as a shortcut. You should not trust it if it is sent to you via an intermediate (i.e. a browser).

### 3. Get the file information

When the upload is done, the client should send back the ticket to one of your endpoints. You should then pull the information about the file.

The endpoint is `GET /uploadRequests/:ticket`.

The ticket can only be used once. It is removed after the first successful use.

#### Scopes


If you are uploading to one of your containers (`appId` == to the app id in your auth token), you need the `uploads/requests/self/status` scope.

If you are uploading to the container of an other app, you need the `uploads/requests/:appId/status/:containerName` scope.

#### Response

If everything goes well, you'll receive the following response:

```json
{ "uploaded": true, 
  "upload": {
    "uploadId": 12345,
    "containerId": 1234,
    "uploader": "<optional> the principal who uploaded the file",
    "url": "the absolute url of the binary content of the file",
    "mimeType": "the mime of the file",
    "sizeBytes": 12344
} }
```

If the upload request still exists but the file has not been uploaded yet, `uploaded` will be set to `false` and `upload` will not exist.
This is not considered a "successful use" of the token, and it won't be deleted. _A strategy for handling uploads could therefore be to poll the url regularly until you get a success._ 

If there is an error, you'll receive this (with a non 200 result code):

```json
{ "success": false, "error": "short_error_code", "errorMessage": "A nicer error message." }
```

## How to list files

To list files in a container, call `GET /files/:app/:container`. You can also query specifically the files uploaded by
a principal: `GET /files/:app/:container/:principal`. Use `anon` for files uploaded by users that are not logged in.

#### Scopes

If you are reading from one of your containers (app auth token and `appId` == to the app id in your auth token), you need the `uploads/containers/self/lost` scope.

If you are reading from the container as a user or as an other app, you need the `uploads/containers/:appId/list/:containerName` scope.

You can also delegate access to your containers (see below).

You always have access to `GET /files/*/*/<your principal>`.

#### Response

If everything goes well, you'll receive the following response:

```json
[{
    "uploadId": 12345,
    "containerId": 1234,
    "uploader": "<optional> the principal who uploaded the file",
    "url": "the absolute url of the binary content of the file",
    "mimeType": "the mime of the file",
    "sizeBytes": 12344
}, ...]
```

If there is an error, you'll receive this (with a non 200 result code):

```json
{ "success": false, "error": "short_error_code", "errorMessage": "A nicer error message." }
```

## Delegation

You can provide tokens for users to list the files in one of your containers for a limited period of time (48 hours).

The endpoint is `POST /delegation`.

The body should be

```json
{
  "principal": "<optional: the principal that will be able to use the token>",
  "appId": 12345,
  "containers": ["container1", "container2"]
}
```

Where `appId` is the id of the app to which all the `containers` belong.
 
The token is returned in the `Session` header and MUST be sent in this header as well.

#### Scopes

_For each delegated container, you need:_

If you are delegating read to one of your containers (`appId` == to the app id in your auth token), you need the `uploads/files/self/lost` scope.

If you are delegating read to the container of an other app, you need the `uploads/files/:appId/list/:containerName` scope.

#### Response

If everything goes well, you'll receive the following response:

```json
{ "success": true, "data": "<the delegation token, can be used in the Session header to access the GET /files/.. endpoints>" }
```

If there is an error, you'll receive this (with a non 200 result code):

```json
{ "success": false, "error": "short_error_code", "errorMessage": "A nicer error message." }
```