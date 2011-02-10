package com.fsck.k9.view;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Environment;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.*;
import com.fsck.k9.Account;
import com.fsck.k9.K9;
import com.fsck.k9.R;
import com.fsck.k9.controller.MessagingController;
import com.fsck.k9.controller.MessagingListener;
import com.fsck.k9.crypto.CryptoProvider;
import com.fsck.k9.crypto.PgpData;
import com.fsck.k9.helper.MediaScannerNotifier;
import com.fsck.k9.helper.SizeFormatter;
import com.fsck.k9.helper.Utility;
import com.fsck.k9.mail.Message;
import com.fsck.k9.mail.MessagingException;
import com.fsck.k9.mail.Part;
import com.fsck.k9.mail.internet.MimeUtility;
import com.fsck.k9.mail.store.LocalStore.LocalAttachmentBodyPart;
import com.fsck.k9.provider.AttachmentProvider;
import org.apache.commons.io.IOUtils;

import java.io.*;



public class MessageCryptoView extends LinearLayout {

    private Context mContext;
    private Activity mActivity;
    private PgpData mPgpData = null;
    private CryptoProvider mCryptoProvider = null;
    private View mMessageContentView;
    private Button mDecryptButton;
    private LinearLayout mCryptoSignatureLayout = null;
    private ImageView mCryptoSignatureStatusImage = null;
    private TextView mCryptoSignatureUserId = null;
    private TextView mCryptoSignatureUserIdRest = null;


    public MessageCryptoView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
    }

    public void setupChildViews() {
        mCryptoSignatureLayout = (LinearLayout) findViewById(R.id.crypto_signature);
        mCryptoSignatureStatusImage = (ImageView) findViewById(R.id.ic_crypto_signature_status);
        mCryptoSignatureUserId = (TextView) findViewById(R.id.userId);
        mCryptoSignatureUserIdRest = (TextView) findViewById(R.id.userIdRest);
        mCryptoSignatureLayout.setVisibility(View.INVISIBLE);
        mDecryptButton = (Button) findViewById(R.id.btn_decrypt);
    }

    public void setActivity(Activity activity) {
        mActivity = activity;

    }

    public void setCryptoProvider(CryptoProvider provider){
        mCryptoProvider = provider;
    }

    public PgpData getCryptoData() {
        return mPgpData;
    }

    public void setCryptoData(PgpData data) {
        if (data == null) {
            mPgpData = new PgpData();
        } else {
            mPgpData = data;
        }
    }

    /**
     * Fill the decrypt layout with signature data, if known, make controls visible, if
     * they should be visible.
     */
    public void updateLayout(final Message message) {
        if (mPgpData.getSignatureKeyId() != 0) {
            mCryptoSignatureUserIdRest.setText(
                mContext.getString(R.string.key_id, Long.toHexString(mPgpData.getSignatureKeyId() & 0xffffffffL)));
            String userId = mPgpData.getSignatureUserId();
            if (userId == null) {
                userId = mContext.getString(R.string.unknown_crypto_signature_user_id);
            }
            String chunks[] = userId.split(" <", 2);
            String name = chunks[0];
            if (chunks.length > 1) {
                mCryptoSignatureUserIdRest.setText("<" + chunks[1]);
            }
            mCryptoSignatureUserId.setText(name);
            if (mPgpData.getSignatureSuccess()) {
                mCryptoSignatureStatusImage.setImageResource(R.drawable.overlay_ok);
            } else if (mPgpData.getSignatureUnknown()) {
                mCryptoSignatureStatusImage.setImageResource(R.drawable.overlay_error);
            } else {
                mCryptoSignatureStatusImage.setImageResource(R.drawable.overlay_error);
            }
            mCryptoSignatureLayout.setVisibility(View.VISIBLE);
            this.setVisibility(View.VISIBLE);
        } else {
            mCryptoSignatureLayout.setVisibility(View.INVISIBLE);
        }
        if (false || ((message == null) && (getDecryptedContent() == null))) {
            this.setVisibility(View.GONE);
            return;
        }
        if (getDecryptedContent() != null) {
            if (mPgpData.getSignatureKeyId() == 0) {
                this.setVisibility(View.GONE);
            } else {
                // no need to show this after decryption/verification
                mDecryptButton.setVisibility(View.GONE);
            }
            return;
        }


        mDecryptButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    String data = null;
                    Part part = MimeUtility.findFirstPartByMimeType(message, "text/plain");
                    if (part == null) {
                        part = MimeUtility.findFirstPartByMimeType(message, "text/html");
                    }
                    if (part != null) {
                        data = MimeUtility.getTextFromPart(part);
                    }
                    mCryptoProvider.decrypt(mActivity, data, mPgpData);
                } catch (MessagingException me) {
                    Log.e(K9.LOG_TAG, "Unable to decrypt email.", me);
                }
            }
        });


        mDecryptButton.setVisibility(View.VISIBLE);
        if (mCryptoProvider.isEncrypted(message)) {
            mDecryptButton.setText(R.string.btn_decrypt);
            this.setVisibility(View.VISIBLE);
        } else if (mCryptoProvider.isSigned(message)) {
            mDecryptButton.setText(R.string.btn_verify);
            this.setVisibility(View.VISIBLE);
        } else {
            this.setVisibility(View.GONE);
            try {
                // check for PGP/MIME encryption
                Part pgp = MimeUtility.findFirstPartByMimeType(message, "application/pgp-encrypted");
                if (pgp != null) {
                    Toast.makeText(mContext, R.string.pgp_mime_unsupported, Toast.LENGTH_LONG).show();
                }
            } catch (MessagingException e) {
                // nothing to do...
            }
        }
    }


    public String getDecryptedContent() {
        return mPgpData.getDecryptedData();
    }
}