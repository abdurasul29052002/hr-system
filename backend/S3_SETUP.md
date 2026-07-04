# S3 Storage Configuration

The application uses AWS S3 (or S3-compatible storage) for file uploads.

## Environment Variables

Configure these environment variables:

```bash
# S3 Configuration
S3_ENABLED=true
S3_REGION=us-east-1
S3_BUCKET_NAME=your-bucket-name
S3_ACCESS_KEY=your-access-key
S3_SECRET_KEY=your-secret-key

# Optional: For MinIO, DigitalOcean Spaces, or other S3-compatible storage
S3_ENDPOINT=https://your-endpoint.com
S3_PATH_STYLE_ACCESS=false  # Set to true for MinIO
```

## AWS S3 Setup

1. **Create S3 Bucket:**
   ```bash
   aws s3 mb s3://your-bucket-name --region us-east-1
   ```

2. **Set Bucket CORS (if accessing from browser):**
   ```json
   [
     {
       "AllowedHeaders": ["*"],
       "AllowedMethods": ["GET", "PUT", "POST", "DELETE"],
       "AllowedOrigins": ["https://your-frontend-domain.com"],
       "ExposeHeaders": []
     }
   ]
   ```

3. **Set Bucket Policy (for public read access - optional):**
   ```json
   {
     "Version": "2012-10-17",
     "Statement": [
       {
         "Sid": "PublicReadGetObject",
         "Effect": "Allow",
         "Principal": "*",
         "Action": "s3:GetObject",
         "Resource": "arn:aws:s3:::your-bucket-name/*"
       }
     ]
   }
   ```

4. **Create IAM User with S3 permissions:**
   - Attach policy: `AmazonS3FullAccess` or custom policy:
   ```json
   {
     "Version": "2012-10-17",
     "Statement": [
       {
         "Effect": "Allow",
         "Action": [
           "s3:PutObject",
           "s3:GetObject",
           "s3:DeleteObject",
           "s3:ListBucket"
         ],
         "Resource": [
           "arn:aws:s3:::your-bucket-name",
           "arn:aws:s3:::your-bucket-name/*"
         ]
       }
     ]
   }
   ```

5. **Get Access Keys:**
   - Go to IAM → Users → Security Credentials → Create Access Key
   - Save `Access Key ID` and `Secret Access Key`

## MinIO Setup (Self-hosted S3)

```bash
# MinIO configuration
S3_ENABLED=true
S3_REGION=us-east-1
S3_BUCKET_NAME=hr-uploads
S3_ACCESS_KEY=minioadmin
S3_SECRET_KEY=minioadmin
S3_ENDPOINT=http://localhost:9000
S3_PATH_STYLE_ACCESS=true

# Run MinIO with Docker
docker run -p 9000:9000 -p 9001:9001 \
  -e MINIO_ROOT_USER=minioadmin \
  -e MINIO_ROOT_PASSWORD=minioadmin \
  minio/minio server /data --console-address ":9001"
```

## DigitalOcean Spaces Setup

```bash
S3_ENABLED=true
S3_REGION=nyc3
S3_BUCKET_NAME=your-space-name
S3_ACCESS_KEY=your-spaces-key
S3_SECRET_KEY=your-spaces-secret
S3_ENDPOINT=https://nyc3.digitaloceanspaces.com
S3_PATH_STYLE_ACCESS=false
```

## Testing

Test upload:
```bash
curl -X POST http://localhost:8080/api/tasks/1/attachments \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "X-Team-Id: 1" \
  -F "file=@test-image.jpg"
```

## File Structure in S3

```
your-bucket-name/
└── tasks/
    ├── 1/
    │   ├── 1234567890_abc12345.jpg
    │   └── 1234567891_def67890.png
    └── 2/
        └── 1234567892_ghi12345.png
```

## Troubleshooting

**Error: S3 storage is not configured**
- Check `S3_ENABLED=true`
- Verify `S3_BUCKET_NAME` is set
- Check `S3_ACCESS_KEY` and `S3_SECRET_KEY`

**Error: Access Denied**
- Verify IAM user has correct permissions
- Check bucket policy allows your operations

**Error: Bucket not found**
- Create bucket first: `aws s3 mb s3://your-bucket-name`
- Verify bucket name in environment variables

**CORS Errors (browser uploads)**
- Configure bucket CORS policy
- Add your frontend domain to AllowedOrigins
